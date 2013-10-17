package com.clueprints.cg;

import java.nio.ByteBuffer;

import org.apache.cassandra.db.marshal.UTF8Type;

import com.beoui.geocell.model.Point;
import com.netflix.astyanax.serializers.AbstractSerializer;
import com.netflix.astyanax.serializers.ByteBufferOutputStream;
import com.netflix.astyanax.serializers.ComparatorType;

public final class PointSerializer extends AbstractSerializer<Point> {

    private static final PointSerializer instance = new PointSerializer();

    public static PointSerializer get() {
        return instance;
    }

    @Override
    public ByteBuffer toByteBuffer(Point obj) {
        if (obj == null) {
            return null;
        }
        
        ByteBufferOutputStream out = new ByteBufferOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(8 + 8);
        buffer.putDouble(obj.getLat());
        buffer.putDouble(obj.getLon());
        buffer.flip();
        
        return buffer;
    }

    @Override
    public Point fromByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return null;
        }
        
        Double lat = byteBuffer.getDouble();
        Double lon = byteBuffer.getDouble();
        
        return new Point(lat, lon);
    }

    @Override
    public ComparatorType getComparatorType() {
        return ComparatorType.BYTESTYPE;
    }

    @Override
    public ByteBuffer fromString(String str) {
        return UTF8Type.instance.fromString(str);
    }

    @Override
    public String getString(ByteBuffer byteBuffer) {
        return UTF8Type.instance.getString(byteBuffer);
    }
}
