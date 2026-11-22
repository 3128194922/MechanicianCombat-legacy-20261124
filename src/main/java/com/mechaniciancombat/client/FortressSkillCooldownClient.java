package com.mechaniciancombat.client;
import com.mechaniciancombat.net.FortressSkillCooldownPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

/** Preserve the visible cooldown fraction when focused shots shorten its duration. */
public final class FortressSkillCooldownClient {
    private static Item tracked;
    private static int remaining;
    private static float initialProgress;
    private static long syncedAt;
    public static void apply(FortressSkillCooldownPayload payload) {
        Minecraft client=Minecraft.getInstance();
        if (client.player==null || client.level==null) { clear(); return; }
        float current=progress(tracked,0);
        if (Float.isNaN(current)) current=client.player.getCooldowns().getCooldownPercent(client.player.getMainHandItem().getItem(),0);
        tracked=client.player.getMainHandItem().getItem(); remaining=Math.max(0,payload.remainingTicks());
        initialProgress=Math.max(0,Math.min(1,current)); syncedAt=client.level.getGameTime();
        if (remaining<=0) client.player.getCooldowns().removeCooldown(tracked);
    }
    public static float progress(Item item,float partial) {
        Minecraft client=Minecraft.getInstance();
        if (item!=tracked || client.level==null || remaining<=0) return Float.NaN;
        double elapsed=client.level.getGameTime()+partial-syncedAt;
        if (elapsed>=remaining) { clear(); return 0; }
        return Math.max(0,Math.min(1,(float)(initialProgress*(remaining-elapsed)/remaining)));
    }
    public static void clear() { tracked=null; remaining=0; initialProgress=0; syncedAt=0; }
    private FortressSkillCooldownClient() { }
}
