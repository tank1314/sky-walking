package com.a.eye.skywalking.storage.data.spandata;

public interface SpanData {

    SpanType getSpanType();

    long getTraceStartTime();

    byte[] toByteArray();

    Long[] getTraceIdSegments();

    String getTraceLevelId();
}
