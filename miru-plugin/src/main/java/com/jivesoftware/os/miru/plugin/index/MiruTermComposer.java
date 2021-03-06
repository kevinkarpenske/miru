package com.jivesoftware.os.miru.plugin.index;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;
import com.jivesoftware.os.filer.io.ByteArrayFiler;
import com.jivesoftware.os.filer.io.FilerIO;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.miru.api.activity.schema.MiruFieldDefinition;
import com.jivesoftware.os.miru.api.activity.schema.MiruFieldDefinition.Prefix;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema.CompositeFieldDefinition;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.plugin.MiruInterner;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.rcvs.marshall.api.UtilLexMarshaller;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 *
 */
public class MiruTermComposer {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final Charset charset;
    private final MiruInterner<MiruTermId> termInterner;

    public MiruTermComposer(final Charset charset, MiruInterner<MiruTermId> termInterner) {
        this.charset = charset;
        this.termInterner = termInterner;
    }

    public MiruTermId compose(MiruSchema schema, MiruFieldDefinition fieldDefinition, StackBuffer stackBuffer, String... parts) throws Exception {
        return termInterner.intern(compose(schema, fieldDefinition, stackBuffer, parts, 0, parts.length));
    }

    private byte[] compose(MiruSchema schema,
        MiruFieldDefinition fieldDefinition,
        StackBuffer stackBuffer,
        String[] parts,
        int offset,
        int length) throws Exception {

        CompositeFieldDefinition[] compositeFieldDefinitions = schema.getCompositeFieldDefinitions(fieldDefinition.fieldId);
        if (compositeFieldDefinitions != null) {
            ByteArrayFiler filer = new ByteArrayFiler(new byte[length * (4 + 1)]); // minimal predicted size
            for (int i = 0; i < length; i++) {
                //TODO optimize
                byte[] bytes = composeBytes(compositeFieldDefinitions[i].definition.prefix, parts[offset + i]);
                // all but last part are length prefixed
                if ((offset + i) < compositeFieldDefinitions.length - 1) {
                    FilerIO.writeInt(filer, bytes.length, "length", stackBuffer);
                }
                filer.write(bytes);
            }
            return filer.getBytes();
        } else {
            return composeBytes(fieldDefinition.prefix, parts[offset]);
        }
    }

    private byte[] composeBytes(MiruFieldDefinition.Prefix p, String part) {
        byte[] termBytes;
        if (p != null && p.type.isAnalyzed()) {
            int sepIndex = part.indexOf(p.separator);
            if (sepIndex < 0) {
                throw new IllegalArgumentException("Term missing separator: " + part);
            }

            String pre = part.substring(0, sepIndex);
            String suf = part.substring(sepIndex + 1);
            byte[] sufBytes = suf.getBytes(charset);

            termBytes = new byte[p.length + sufBytes.length];
            writePrefixBytes(p, pre, termBytes);
            System.arraycopy(sufBytes, 0, termBytes, p.length, sufBytes.length);
        } else {
            termBytes = part.getBytes(charset);
        }
        return termBytes;
    }

    public String[] decompose(MiruSchema schema, MiruFieldDefinition fieldDefinition, StackBuffer stackBuffer, MiruTermId term) throws IOException {
        CompositeFieldDefinition[] compositeFieldDefinitions = schema.getCompositeFieldDefinitions(fieldDefinition.fieldId);
        if (compositeFieldDefinitions != null) {
            String[] parts = new String[compositeFieldDefinitions.length];
            byte[] termBytes = term.getBytes();
            ByteArrayFiler filer = new ByteArrayFiler(termBytes);
            for (int i = 0; i < compositeFieldDefinitions.length; i++) {
                //TODO optimize
                // all but last part are length prefixed
                if (i < compositeFieldDefinitions.length - 1) {
                    int length = FilerIO.readInt(filer, "length", stackBuffer);
                    parts[i] = decomposeBytes(compositeFieldDefinitions[i].definition, termBytes, (int) filer.getFilePointer(), length);
                    filer.skip(length);
                } else {
                    parts[i] = decomposeBytes(compositeFieldDefinitions[i].definition, termBytes, (int) filer.getFilePointer(),
                        (int) (filer.length() - filer.getFilePointer()));
                }
            }
            return parts;
        } else {
            byte[] bytes = term.getBytes();
            return new String[] { decomposeBytes(fieldDefinition, bytes, 0, bytes.length) };
        }
    }

    private String decomposeBytes(MiruFieldDefinition fieldDefinition, byte[] termBytes, int offset, int length) {
        MiruFieldDefinition.Prefix p = fieldDefinition.prefix;
        if (p != null && p.type.isAnalyzed()) {
            String pre = readPrefixBytes(p, termBytes, offset);
            String suf = new String(termBytes, offset + p.length, length - p.length, charset);
            return pre + (char) p.separator + suf;
        } else {
            return new String(termBytes, offset, length, charset);
        }
    }

    private void writePrefixBytes(MiruFieldDefinition.Prefix p, String pre, byte[] termBytes) {
        if (p.type == MiruFieldDefinition.Prefix.Type.raw) {
            byte[] preBytes = pre.getBytes(charset);
            // one byte for length
            if (preBytes.length > (p.length - 1)) {
                throw new IllegalArgumentException("Prefix overflow: " + preBytes.length + " > " + (p.length - 1) + " (did you forget 1 byte for length?)");
            }
            System.arraycopy(preBytes, 0, termBytes, 0, preBytes.length);
            termBytes[p.length - 1] = (byte) preBytes.length;
        } else if (p.type == MiruFieldDefinition.Prefix.Type.numeric) {
            if (p.length == 4) {
                int v = Integer.parseInt(pre);
                byte[] vBytes = UtilLexMarshaller.intToLex(v); //TODO would be nice to marshal to dest array
                System.arraycopy(vBytes, 0, termBytes, 0, 4);
            } else if (p.length == 8) {
                long v = Long.parseLong(pre);
                byte[] vBytes = UtilLexMarshaller.longToLex(v); //TODO would be nice to marshal to dest array
                System.arraycopy(vBytes, 0, termBytes, 0, 8);
            } else {
                throw new IllegalStateException("Numeric prefix only supports int and long");
            }
        } else {
            throw new IllegalArgumentException("No prefix");
        }
    }

    private String readPrefixBytes(MiruFieldDefinition.Prefix p, byte[] termBytes, int offset) {
        if (p.type == MiruFieldDefinition.Prefix.Type.raw) {
            int length = (int) termBytes[offset + p.length - 1];
            return new String(termBytes, offset, length, charset);
        } else if (p.type == MiruFieldDefinition.Prefix.Type.numeric) {
            if (p.length == 4) {
                try {
                    int value = UtilLexMarshaller.intFromLex(termBytes, offset);
                    return String.valueOf(value);
                } catch (ArrayIndexOutOfBoundsException e) {
                    LOG.error("Failed to deserialize prefix", e);
                    throw e;
                }
            } else if (p.length == 8) {
                try {
                    long value = UtilLexMarshaller.longFromLex(termBytes, offset);
                    return String.valueOf(value);
                } catch (ArrayIndexOutOfBoundsException e) {
                    LOG.error("Failed to deserialize prefix", e);
                    throw e;
                }
            } else {
                throw new IllegalStateException("Numeric prefix only supports int and long");
            }
        } else {
            throw new IllegalArgumentException("No prefix");
        }
    }

    public byte[] prefixLowerInclusive(MiruSchema schema, MiruFieldDefinition fieldDefinition, StackBuffer stackBuffer, String... parts) throws Exception {
        CompositeFieldDefinition[] compositeFieldDefinitions = schema.getCompositeFieldDefinitions(fieldDefinition.fieldId);
        if (compositeFieldDefinitions != null) {
            Preconditions.checkArgument(parts.length <= compositeFieldDefinitions.length,
                "Provided more value parts than we have composite field definitions");
            //TODO optimize
            int headUpperBound = Math.min(compositeFieldDefinitions.length - 1, parts.length);
            byte[] headBytes = compose(schema, fieldDefinition, stackBuffer, parts, 0, headUpperBound);
            if (parts.length == compositeFieldDefinitions.length) {
                int tailIndex = parts.length - 1;
                byte[] tailBytes;
                if (parts[tailIndex].indexOf(compositeFieldDefinitions[tailIndex].definition.prefix.separator) > -1) {
                    tailBytes = composeBytes(compositeFieldDefinitions[tailIndex].definition.prefix, parts[tailIndex]);
                } else {
                    tailBytes = prefixLowerInclusiveBytes(compositeFieldDefinitions[tailIndex].definition.prefix, parts[tailIndex]);
                }
                return Bytes.concat(headBytes, tailBytes);
            } else {
                return headBytes;
            }
        } else {
            Preconditions.checkArgument(parts.length == 1, "Provided multiple value parts for a non-composite field");
            return prefixLowerInclusiveBytes(fieldDefinition.prefix, parts[0]);
        }
    }

    private byte[] prefixLowerInclusiveBytes(MiruFieldDefinition.Prefix p, String pre) {
        if (p.type == MiruFieldDefinition.Prefix.Type.raw || p.type == MiruFieldDefinition.Prefix.Type.wildcard) {
            return pre.getBytes(charset);
        } else if (p.type == MiruFieldDefinition.Prefix.Type.numeric) {
            int v = Integer.parseInt(pre);
            return UtilLexMarshaller.intToLex(v);
        } else {
            throw new IllegalArgumentException("Can't range filter this field!");
        }
    }

    private static final byte[] NULL_BYTE = new byte[] { 0 };

    public byte[] prefixUpperExclusive(MiruSchema schema, MiruFieldDefinition fieldDefinition, StackBuffer stackBuffer, String... parts) throws Exception {
        CompositeFieldDefinition[] compositeFieldDefinitions = schema.getCompositeFieldDefinitions(fieldDefinition.fieldId);
        if (compositeFieldDefinitions != null) {
            Preconditions.checkArgument(parts.length <= compositeFieldDefinitions.length,
                "Provided more value parts than we have composite field definitions");
            //TODO optimize
            int headUpperBound = Math.min(compositeFieldDefinitions.length - 1, parts.length);
            byte[] headBytes = compose(schema, fieldDefinition, stackBuffer, parts, 0, headUpperBound);
            if (parts.length == compositeFieldDefinitions.length) {
                int tailIndex = parts.length - 1;
                byte[] tailBytes;
                if (parts[tailIndex].indexOf(compositeFieldDefinitions[tailIndex].definition.prefix.separator) > -1) {
                    // OMG  need new byte[]{0} to make exclusive
                    tailBytes = Bytes.concat(composeBytes(compositeFieldDefinitions[tailIndex].definition.prefix, parts[tailIndex]), NULL_BYTE);
                } else {
                    tailBytes = prefixUpperExclusiveBytes(compositeFieldDefinitions[tailIndex].definition.prefix, parts[tailIndex]);
                }
                return Bytes.concat(headBytes, tailBytes);
            } else {
                makeUpperExclusive(headBytes);
                return headBytes;
            }
        } else {
            Preconditions.checkArgument(parts.length == 1, "Provided multiple value parts for a non-composite field");
            return prefixUpperExclusiveBytes(fieldDefinition.prefix, parts[0]);
        }
    }

    public byte[] prefixUpperExclusiveBytes(Prefix p, String pre) {
        if (p.type == MiruFieldDefinition.Prefix.Type.wildcard) {
            byte[] raw = pre.getBytes(charset);
            makeUpperExclusive(raw);
            return raw;
        } else if (p.type == MiruFieldDefinition.Prefix.Type.raw) {
            byte[] preBytes = pre.getBytes(charset);
            byte[] raw = new byte[p.length];
            System.arraycopy(preBytes, 0, raw, 0, preBytes.length);

            // given: [64,72,96,127]
            // want: [64,72,97,-128]
            makeUpperExclusive(raw);
            return raw;
        } else if (p.type == MiruFieldDefinition.Prefix.Type.numeric) {
            int v = Integer.parseInt(pre) + 1;
            return UtilLexMarshaller.intToLex(v);
        } else {
            throw new IllegalArgumentException("Can't range filter this field!");
        }
    }

    public static void makeUpperExclusive(byte[] raw) {
        // given: [64,72,96,0] want: [64,72,97,1]
        // given: [64,72,96,127] want: [64,72,96,-128] because -128 is the next lex value after 127
        // given: [64,72,96,-1] want: [64,72,97,0] because -1 is the lex largest value and we roll to the next digit
        for (int i = raw.length - 1; i >= 0; i--) {
            if (raw[i] == -1) {
                raw[i] = 0;
            } else if (raw[i] == Byte.MAX_VALUE) {
                raw[i] = Byte.MIN_VALUE;
                break;
            } else {
                raw[i]++;
                break;
            }
        }
    }
}
