package io.coredge.health.claims.helpers;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.util.TimeZone;

public class TimeZoneTypeAdapter extends TypeAdapter<TimeZone> {
    @Override
    public void write(JsonWriter out, TimeZone value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.getID()); // Serialize TimeZone as its ID string
        }
    }

    @Override
    public TimeZone read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return TimeZone.getTimeZone(in.nextString()); // Deserialize ID string to TimeZone
    }
}