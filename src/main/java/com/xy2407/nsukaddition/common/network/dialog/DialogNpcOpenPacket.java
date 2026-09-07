package com.xy2407.nsukaddition.common.network.dialog;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.DialogNpcOpenBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端发起的对话框打开包，携带标题/正文/对话选项列表及其动作ID与NPC实体ID，客户端据此渲染对话框。 */
public record DialogNpcOpenPacket(String title, String body, List<String> options, List<String> actions, int dialogEntityId) implements CustomPacketPayload {

    public static final Type<DialogNpcOpenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "dialog_npc_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogNpcOpenPacket> STREAM_CODEC =
            StreamCodec.of(DialogNpcOpenPacket::encode, DialogNpcOpenPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, DialogNpcOpenPacket p) {
        buf.writeUtf(p.title() == null ? "" : p.title());
        buf.writeUtf(p.body() == null ? "" : p.body());
        writeList(buf, p.options());
        writeList(buf, p.actions());
        buf.writeInt(p.dialogEntityId());
    }

    private static void writeList(RegistryFriendlyByteBuf buf, List<String> list) {
        List<String> items = list == null ? List.of() : list;
        buf.writeVarInt(items.size());
        for (String item : items) {
            buf.writeUtf(item == null ? "" : item);
        }
    }

    public static DialogNpcOpenPacket decode(RegistryFriendlyByteBuf buf) {
        String title = buf.readUtf(256);
        String body = buf.readUtf(4096);
        List<String> options = readList(buf);
        List<String> actions = readList(buf);
        int entityId = buf.readInt();
        return new DialogNpcOpenPacket(title, body, options, actions, entityId);
    }

    private static List<String> readList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf(256));
        }
        return list;
    }

    public static void handle(DialogNpcOpenPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DialogNpcOpenBridge.open(p.title(), p.body(),
                p.options() == null ? List.of() : p.options(),
                p.actions() == null ? List.of() : p.actions(),
                p.dialogEntityId()));
    }
}