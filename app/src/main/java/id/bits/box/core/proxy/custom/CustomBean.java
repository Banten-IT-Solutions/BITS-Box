package id.bits.box.proxy.custom;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import id.bits.box.fmt.AbstractBean;
import id.bits.box.fmt.KryoConverters;
import id.bits.box.ktx.Logs;

public class CustomBean extends AbstractBean {

    public String plgId;
    public String protocolId;
    public JSONObject sharedStorage = new JSONObject();

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocolId == null) protocolId = "";
        if (plgId == null) plgId = "bits.box.plugin.donotexist";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(plgId);
        output.writeString(protocolId);
        output.writeString(sharedStorage.toString());
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        plgId = input.readString();
        protocolId = input.readString();
        sharedStorage = tryParseJSON(input.readString());
    }

    @NotNull
    public static JSONObject tryParseJSON(String input) {
        JSONObject ret;
        try {
            ret = new JSONObject(input);
        } catch (Exception e) {
            ret = new JSONObject();
            Logs.INSTANCE.e(e);
        }
        return ret;
    }

    public String displayType() {
        return "invalid";
    }

    @Override
    public boolean canMapping() {
        return false;
    }

    @Override
    public boolean canICMPing() {
        return false;
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public CustomBean clone() {
        return KryoConverters.deserialize(new CustomBean(), KryoConverters.serialize(this));
    }

    public static final Creator<CustomBean> CREATOR = new CREATOR<CustomBean>() {
        @NonNull
        @Override
        public CustomBean newInstance() {
            return new CustomBean();
        }

        @Override
        public CustomBean[] newArray(int size) {
            return new CustomBean[size];
        }
    };
}