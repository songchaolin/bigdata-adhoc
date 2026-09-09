package io.gitee.songchaolin.adhoc.storage.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gitee.songchaolin.adhoc.storage.model.ResultColumn;
import io.gitee.songchaolin.adhoc.storage.model.ResultRow;
import io.gitee.songchaolin.adhoc.storage.model.ResultSchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * schema / row 的 JSON 序列化（Jackson）。
 * schema JSON：[{"colIndex":0,"colName":"id","colType":"BIGINT"}, ...]
 * row JSON：[1, "hello", ...]
 */
public final class AdhocResultSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AdhocResultSerializer() {
    }

    public static byte[] schemaToJson(ResultSchema schema) throws IOException {
        return MAPPER.writeValueAsBytes(schema.getColumns());
    }

    public static ResultSchema schemaFromJson(byte[] bytes) throws IOException {
        List<ResultColumn> cols = MAPPER.readValue(bytes,
                MAPPER.getTypeFactory().constructCollectionType(List.class, ResultColumn.class));
        return new ResultSchema(cols);
    }

    public static byte[] rowToJson(ResultRow row) throws IOException {
        return MAPPER.writeValueAsBytes(row.getValues());
    }

    public static ResultRow rowFromJson(byte[] bytes) throws IOException {
        return new ResultRow(MAPPER.readValue(bytes, Object[].class));
    }

    /** 序列化结果为字节（MAGIC + schema 行 + 数据行，长度前缀格式），用于 StorageClient.upload。 */
    public static byte[] serialize(ResultSchema schema, List<ResultRow> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AdhocResultFormat.MAGIC);
        byte[] schemaJson = schemaToJson(schema);
        AdhocResultFormat.writeInt(out, schemaJson.length);
        out.write(schemaJson);
        for (ResultRow row : rows) {
            byte[] rowBytes = rowToJson(row);
            AdhocResultFormat.writeInt(out, rowBytes.length);
            out.write(rowBytes);
        }
        return out.toByteArray();
    }
}