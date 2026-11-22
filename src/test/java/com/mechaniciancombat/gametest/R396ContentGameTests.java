package com.mechaniciancombat.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import com.mechaniciancombat.entity.MoltenGolemEntity;
import com.mechaniciancombat.registry.ModRegistries;
import com.mechaniciancombat.net.GunStatePayload;
import com.mechaniciancombat.net.CombatBackpackStatusPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.UUID;

@GameTestHolder("tkgun")
@PrefixGameTestTemplate(false)
public final class R396ContentGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void slidingFocusRetainsAndDecaysInertia(GameTestHelper helper) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "slide_focus_inertia"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        var state = com.mechaniciancombat.TkGunServer.state(player);
        long now = helper.getLevel().getGameTime();
        state.state = com.mechaniciancombat.TkGun.State.SLIDE;
        state.slideUntil = now + 10;
        state.slideDirX = 1;
        state.slideDirZ = 0;
        player.setDeltaMovement(0, 0.2, 0);
        com.mechaniciancombat.TkGunServer.startSlideBrace(player);
        helper.assertTrue(Math.abs(player.getDeltaMovement().x - 0.18) < 1.0E-6,
                "sliding focus must initially preserve forward inertia");
        var tick = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("tickSlideFocusInertia",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class, long.class);
        tick.setAccessible(true);
        tick.invoke(null, player, state, now + 3);
        helper.assertTrue(Math.abs(player.getDeltaMovement().x - 0.18 * 4.0 / 7.0) < 1.0E-6
                        && Math.abs(player.getDeltaMovement().y - 0.2) < 1.0E-6,
                "sliding focus inertia must decay while preserving vertical velocity");
        state.state = com.mechaniciancombat.TkGun.State.IDLE;
        player.setDeltaMovement(0.5, 0.2, 0);
        tick.invoke(null, player, state, now + 4);
        helper.assertTrue(player.getDeltaMovement().x == 0.5 && state.slideFastFocusUntil == 0,
                "inertia must stop immediately when the focus transition is interrupted");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void combatRollCooldownDoesNotTriggerFallback(GameTestHelper helper) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "roll_cooldown"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        var state = com.mechaniciancombat.TkGunServer.state(player);
        var input = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("handleRollInput",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.net.GunRollPayload.class, boolean.class);
        input.setAccessible(true);
        var request = new com.mechaniciancombat.net.GunRollPayload(0, 0, 0);
        var before = player.getDeltaMovement();
        input.invoke(null, player, request, true);
        helper.assertTrue(state.state == com.mechaniciancombat.TkGun.State.IDLE
                        && state.dashUntil == 0 && player.getDeltaMovement().equals(before),
                "Combat Roll key without a successful event must not start a roll or grant a bonus");
        state.state = com.mechaniciancombat.TkGun.State.OVERHEAT;
        input.invoke(null, player, request, true);
        helper.assertTrue(state.state == com.mechaniciancombat.TkGun.State.OVERHEAT && state.dashUntil == 0,
                "a rejected roll must not interrupt overheat");
        state.state = com.mechaniciancombat.TkGun.State.IDLE;
        com.mechaniciancombat.TkGunServer.onRollStarted(player);
        helper.assertTrue(state.state == com.mechaniciancombat.TkGun.State.ROLL,
                "a real Combat Roll event must still start the gun roll state");
        state.state = com.mechaniciancombat.TkGun.State.IDLE;
        state.lastDodgeKind = 1;
        state.deployed = false;
        state.dashUntil = helper.getLevel().getGameTime() + 40;
        input.invoke(null, player, request, true);
        helper.assertTrue(state.state == com.mechaniciancombat.TkGun.State.SLIDE,
                "roll follow-up must still start a slide during Combat Roll cooldown");
        var nativePlayer = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "native_roll"));
        nativePlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        input.invoke(null, nativePlayer, request, false);
        helper.assertTrue(com.mechaniciancombat.TkGunServer.state(nativePlayer).state == com.mechaniciancombat.TkGun.State.ROLL,
                "native roll remains available without Combat Roll");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void slideUsesDodgeInputDirection(GameTestHelper helper) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "slide_direction"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        player.setYRot(0);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        var start = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("startModSlide",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class, long.class);
        start.setAccessible(true);
        for (int forward = -1; forward <= 1; forward++) {
            for (int left = -1; left <= 1; left++) {
                var direction = com.mechaniciancombat.RollDirection.fromInput(forward, left, 90);
                state.rollIntentX = direction.x();
                state.rollIntentZ = direction.z();
                state.state = com.mechaniciancombat.TkGun.State.IDLE;
                player.setDeltaMovement(0, 0.2, 0.4);
                start.invoke(null, player, state, helper.getLevel().getGameTime());
                double speed = 2.0D * com.mechaniciancombat.TkGun.SLIDE_DISTANCE / com.mechaniciancombat.TkGun.SLIDE_TICKS;
                var velocity = player.getDeltaMovement();
                helper.assertTrue(Math.abs(state.slideDirX - direction.x()) < 1.0E-6
                        && Math.abs(state.slideDirZ - direction.z()) < 1.0E-6
                        && Math.abs(velocity.x - direction.x() * speed) < 1.0E-6
                        && Math.abs(velocity.z - direction.z() * speed) < 1.0E-6
                        && Math.abs(velocity.y - 0.2) < 1.0E-6,
                        "slide direction must follow current WASD and yaw, including default forward");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void completedSlideExtendsSpeedOnly(GameTestHelper helper) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "slide_speed_tail"));
        var state = com.mechaniciancombat.TkGunServer.state(player);
        long now = helper.getLevel().getGameTime() + 100;
        state.dashUntil = now + 10;
        state.slideUntil = now;
        state.slideMeasuring = true;
        var slide = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("tickSlide",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class, long.class);
        slide.setAccessible(true);
        slide.invoke(null, player, state, now);
        com.mechaniciancombat.GunStateMachine.tickDash(player, state, now + 10);
        double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > base,
                "completed slide must keep speed after the dodge window expires");
        helper.assertTrue(!state.dashSyncActive, "speed tail must not extend the synced dodge window");
        slide.invoke(null, player, state, now + 11);
        com.mechaniciancombat.GunStateMachine.tickDash(player, state, now + 50);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1.0E-6,
                "the speed tail must expire after exactly 40 ticks and must not stack twice");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void focusRequiresStandingOnBlock(GameTestHelper helper) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "focus_ground"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setOnGround(false);
        helper.assertTrue(!com.mechaniciancombat.TkGunServer.startFocus(player),
                "focus must not start in midair");
        helper.assertTrue(!com.mechaniciancombat.TkGunServer.state(player).deployed,
                "midair focus must not change deployment state");
        helper.setBlock(new BlockPos(3, 2, 3), net.minecraft.world.level.block.Blocks.STONE);
        player.setOnGround(true);
        helper.assertTrue(com.mechaniciancombat.TkGunServer.startFocus(player),
                "focus must start on a supporting block");
        com.mechaniciancombat.TkGunServer.stopFocus(player);
        helper.setBlock(new BlockPos(3, 2, 3), net.minecraft.world.level.block.Blocks.OAK_SLAB);
        player.moveTo(pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5);
        player.setOnGround(true);
        helper.assertTrue(com.mechaniciancombat.TkGunServer.startFocus(player),
                "focus must start on a supporting slab");
        helper.setBlock(new BlockPos(3, 2, 3), net.minecraft.world.level.block.Blocks.WATER);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        var enforce = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("enforceFocusSupport",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class);
        enforce.setAccessible(true);
        enforce.invoke(null, player, state);
        helper.assertTrue(!state.deployed, "focus must end when its supporting block disappears");
        player.setOnGround(true);
        helper.assertTrue(!com.mechaniciancombat.TkGunServer.startFocus(player),
                "water must not count as a supporting block");
        for (var dodge : new com.mechaniciancombat.TkGun.State[] {
                com.mechaniciancombat.TkGun.State.ROLL, com.mechaniciancombat.TkGun.State.SLIDE}) {
            state.state = dodge;
            helper.assertTrue(!com.mechaniciancombat.TkGunServer.startFocus(player) && state.state == dodge,
                    "unsupported quick focus must not interrupt a dodge");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void commandBlockTamesOnlyUnownedGolem(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "golem_owner"));
        var other = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "golem_other"));
        var golem = ModRegistries.MOLTEN_GOLEM.get().create(level);
        golem.moveTo(owner.getX(), owner.getY(), owner.getZ());
        golem.tick();
        helper.assertTrue(!golem.isSummonedBy(owner.getUUID()), "unowned golem claimed nearest player");
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMMAND_BLOCK));
        owner.interactOn(golem, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(golem.isSummonedBy(owner.getUUID()), "command block did not tame golem");
        helper.assertTrue(owner.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount() == 1,
                "command block must not be consumed");
        helper.assertTrue(golem.isAlliedTo(owner), "owner must be allied");
        var wolf = EntityType.WOLF.create(level);
        wolf.setOwnerUUID(owner.getUUID());
        helper.assertTrue(golem.isAlliedTo(wolf), "owner's pets must be allied");
        CompoundTag saved = new CompoundTag();
        golem.saveWithoutId(saved);
        var restored = ModRegistries.MOLTEN_GOLEM.get().create(level);
        restored.load(saved);
        helper.assertTrue(restored.isSummonedBy(owner.getUUID()), "tamed owner was not saved");
        other.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMMAND_BLOCK));
        other.interactOn(golem, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(golem.isSummonedBy(owner.getUUID()), "another player stole owned golem");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void golemRetaliatesAgainstNonAlliedAttacker(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "retaliation_owner"));
        var golem = ModRegistries.MOLTEN_GOLEM.get().create(level);
        golem.setSummoner(owner.getUUID());
        var saved = new CompoundTag();
        golem.saveWithoutId(saved);
        saved.putInt("SummonTicks", 0);
        golem.load(saved);
        var attacker = EntityType.ZOMBIE.create(level);
        golem.hurt(level.damageSources().mobAttack(attacker), 1.0F);
        helper.assertTrue(golem.getTarget() == attacker, "golem must immediately target its non-allied attacker");
        golem.setTarget(null);
        var pet = EntityType.WOLF.create(level);
        pet.setOwnerUUID(owner.getUUID());
        golem.invulnerableTime = 0;
        golem.hurt(level.damageSources().mobAttack(pet), 1.0F);
        helper.assertTrue(golem.getTarget() == null, "golem must not target its owner's pet");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mechanicianArmorRequiresUndersuit(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "mechanician_armor"));
        var undersuit = ForgeRegistries.ITEMS.getValue(id("mechanician_undersuit"));
        var chest = ForgeRegistries.ITEMS.getValue(id("mechanician_chestplate"));
        var legs = ForgeRegistries.ITEMS.getValue(id("mechanician_leggings"));
        var boots = ForgeRegistries.ITEMS.getValue(id("mechanician_boots"));
        helper.assertTrue(undersuit != null && chest instanceof net.minecraft.world.item.ArmorItem
                && legs instanceof net.minecraft.world.item.ArmorItem && boots instanceof net.minecraft.world.item.ArmorItem,
                "mechanician armor and undersuit must be registered");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(chest));
        var denied = chest.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(denied.getResult() == net.minecraft.world.InteractionResult.FAIL,
                "chestplate must reject use without an equipped undersuit");
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var charm = curios.getStacksHandler("charm").orElseThrow().getStacks();
        var back = curios.getStacksHandler("back").orElseThrow().getStacks();
        helper.assertTrue(charm.isItemValid(0, new net.minecraft.world.item.ItemStack(undersuit)),
                "undersuit must be eligible for the default charm slot");
        back.setStackInSlot(0, new net.minecraft.world.item.ItemStack(undersuit));
        helper.assertTrue(com.mechaniciancombat.item.MechanicianUndersuitItem.isEquipped(player),
                "undersuit must be recognized in any equipped Curios slot");
        back.setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        charm.setStackInSlot(0, new net.minecraft.world.item.ItemStack(undersuit));
        back.setStackInSlot(0, new net.minecraft.world.item.ItemStack(ModRegistries.COMBAT_BACKPACK.get()));
        helper.assertTrue(chest.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "chestplate must equip while undersuit and backpack are both equipped");
        helper.assertTrue(!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
                "chestplate must occupy chest slot");
        charm.setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        var enforce = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("enforceMechanicianArmorRequirement",
                net.minecraft.server.level.ServerPlayer.class);
        enforce.setAccessible(true);
        enforce.invoke(null, player);
        helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
                "removing undersuit must remove protected armor from its equipment slot");
        helper.assertTrue(player.getInventory().contains(new net.minecraft.world.item.ItemStack(chest)),
                "removed armor must be returned to the inventory");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullArmorAllowsUnarmedDodge(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "armor_dodge"));
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        curios.getStacksHandler("charm").orElseThrow().getStacks().setStackInSlot(0,
                new net.minecraft.world.item.ItemStack(ModRegistries.MECHANICIAN_UNDERSUIT.get()));
        var backpack = new net.minecraft.world.item.ItemStack(ModRegistries.COMBAT_BACKPACK.get());
        curios.getStacksHandler("back").orElseThrow().getStacks().setStackInSlot(0, backpack);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(ModRegistries.MECHANICIAN_CHESTPLATE.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(ModRegistries.MECHANICIAN_LEGGINGS.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(ModRegistries.MECHANICIAN_BOOTS.get()));
        var input = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("handleRollInput",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.net.GunRollPayload.class, boolean.class);
        input.setAccessible(true);
        var request = new com.mechaniciancombat.net.GunRollPayload(0, 0, 0);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        input.invoke(null, player, request, false);
        helper.assertTrue(state.state == com.mechaniciancombat.TkGun.State.IDLE, "armor without backpack core must not dodge");
        com.mechaniciancombat.item.BackpackUpgradeData.installCore(backpack);
        input.invoke(null, player, request, false);
        helper.assertTrue(state.state == com.mechaniciancombat.TkGun.State.ROLL, "full armor and backpack core must allow unarmed dodge");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void r611WeaponsAreRegisteredAsGuns(GameTestHelper helper) {
        for (String name : new String[]{"bowgun", "abyssal_ancient_cannon", "veritas_et_justitia"}) {
            var item = ForgeRegistries.ITEMS.getValue(id(name));
            helper.assertTrue(item != null && com.mechaniciancombat.TkGun.isGun(new net.minecraft.world.item.ItemStack(item)),
                    name + " must be registered and recognized as a gun");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void switchingGunsKeepsMagazineOnOriginalStack(GameTestHelper helper) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "gun_switch_mag"));
        var oldGun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var newGun = new net.minecraft.world.item.ItemStack(ModRegistries.ABYSSAL_ANCIENT_CANNON.get());
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(newGun, 5, "tkgun:bolt");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, newGun);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        state.lastHeldGun = oldGun;
        state.magLoaded = true;
        state.mag = 3;
        state.magAmmo = "tkgun:bolt";
        state.lastSavedMag = -1;
        var persist = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("persistHipMagazine",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class);
        persist.setAccessible(true);
        persist.invoke(null, player, state);
        helper.assertTrue(com.mechaniciancombat.item.GunAmmoData.savedMagazineCount(oldGun) == 3,
                "old magazine state must be stored on the original gun");
        helper.assertTrue(com.mechaniciancombat.item.GunAmmoData.savedMagazineCount(newGun) == 5,
                "switching must not overwrite the new gun's magazine");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void switchingGunsBeforeTickClearsOldShots(GameTestHelper helper) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "gun_switch_queued"));
        var oldGun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var newGun = new net.minecraft.world.item.ItemStack(ModRegistries.ABYSSAL_ANCIENT_CANNON.get());
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(oldGun, 3, "tkgun:bolt");
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(newGun, 5, "tkgun:bolt");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, newGun);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        state.lastHeldGun = oldGun;
        state.magLoaded = true;
        state.mag = 3;
        state.magAmmo = "tkgun:bolt";
        state.lastSavedMag = 3;
        state.lastSavedAmmo = "tkgun:bolt";
        state.shotQueue.add(com.mechaniciancombat.TkGunServer.Pending.shot(3, "tkgun:bolt"));
        var load = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("ensureMagazineLoaded",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class);
        load.setAccessible(true);
        load.invoke(null, player, state);
        helper.assertTrue(state.shotQueue.isEmpty(), "a shot from the old gun must not fire from the new gun");
        helper.assertTrue(com.mechaniciancombat.item.GunAmmoData.savedMagazineCount(oldGun) == 2,
                "the canceled shot must be refunded to the original gun");
        helper.assertTrue(com.mechaniciancombat.item.GunAmmoData.savedMagazineCount(newGun) == 5 && state.mag == 5,
                "the new gun must retain its own magazine state");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void undersuitVisibilityMatchesCurios(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "undersuit_visibility"));
        var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var slot = inventory.getStacksHandler("charm").orElseThrow();
        var suit = new net.minecraft.world.item.ItemStack(ModRegistries.MECHANICIAN_UNDERSUIT.get());
        slot.getStacks().setStackInSlot(0, suit);
        slot.getRenders().set(0, true);
        helper.assertTrue(com.mechaniciancombat.item.MechanicianUndersuitItem.isVisiblyEquipped(player), "visible functional suit must render");
        slot.getRenders().set(0, false);
        helper.assertTrue(!com.mechaniciancombat.item.MechanicianUndersuitItem.isVisiblyEquipped(player), "hidden suit must not hide skin");
        helper.assertTrue(com.mechaniciancombat.item.MechanicianUndersuitItem.isEquipped(player), "hidden functional suit must still work");
        slot.getCosmeticStacks().setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        slot.getRenders().set(0, true);
        helper.assertTrue(!com.mechaniciancombat.item.MechanicianUndersuitItem.isVisiblyEquipped(player), "different cosmetic overrides the suit");
        slot.getStacks().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        slot.getCosmeticStacks().setStackInSlot(0, suit.copy());
        slot.getRenders().set(0, false);
        helper.assertTrue(com.mechaniciancombat.item.MechanicianUndersuitItem.isVisiblyEquipped(player), "cosmetic suit remains visible with functional toggle off");
        helper.assertTrue(!com.mechaniciancombat.item.MechanicianUndersuitItem.isEquipped(player), "cosmetic suit must not grant gameplay effects");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void multipleUndersuitsDoNotStackArmor(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "undersuit_attributes"));
        var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var charm = inventory.getStacksHandler("charm").orElseThrow().getStacks();
        var back = inventory.getStacksHandler("back").orElseThrow().getStacks();
        var suit = new net.minecraft.world.item.ItemStack(ModRegistries.MECHANICIAN_UNDERSUIT.get());
        charm.setStackInSlot(0, suit);
        back.setStackInSlot(0, suit.copy());
        var item = (com.mechaniciancombat.item.MechanicianUndersuitItem) suit.getItem();
        var a = new top.theillusivec4.curios.api.SlotContext("charm", player, 0, false, true);
        var b = new top.theillusivec4.curios.api.SlotContext("back", player, 0, false, true);
        var modsA = item.getAttributeModifiers(a, UUID.randomUUID(), suit);
        var modsB = item.getAttributeModifiers(b, UUID.randomUUID(), suit);
        double base = player.getAttributeValue(Attributes.ARMOR);
        player.getAttributes().addTransientAttributeModifiers(modsA);
        player.getAttributes().addTransientAttributeModifiers(modsB);
        helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) == base + 3,
                "multiple undersuits must grant only one netherite helmet armor bonus");
        charm.setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        player.getAttributes().removeAttributeModifiers(modsA);
        item.curioTick(b, suit);
        helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) == base + 3,
                "removing one suit must retain the bonus from another equipped suit");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sprintDirectionUsesCameraAndDashTurnLimit(GameTestHelper helper) {

        helper.assertTrue(com.mechaniciancombat.SprintDirectionMath.movementYaw(0,1,0)==0 && com.mechaniciancombat.SprintDirectionMath.movementYaw(0,0,1)==-90,
                "forward and left movement must align to the camera");
        helper.assertTrue(com.mechaniciancombat.SprintDirectionMath.movementYaw(90,1,1)==45, "diagonal movement must use the camera-relative direction");
        helper.assertTrue(com.mechaniciancombat.SprintDirectionMath.steerDashYaw(0,90)==4 && com.mechaniciancombat.SprintDirectionMath.steerDashYaw(179,-179)==-179,
                "dash steering must limit rotation and use the shortest turn");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void airborneLandingHalvesDamageAndRolls(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"airborne_roll_test"));
        var pos=helper.absolutePos(new BlockPos(8,4,8));
        player.moveTo(pos.getX(),pos.getY(),pos.getZ(),0,0); player.setOnGround(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        var st=com.mechaniciancombat.TkGunServer.state(player);
        helper.assertTrue(com.mechaniciancombat.TkGunServer.applyAirborneLandingDamageReduction(player,7)==3 && st.landingRollArmed,
                "ordinary landing damage must be halved and arm a roll");
        var tick=com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("tickLongAirborne",net.minecraft.server.level.ServerPlayer.class,
                com.mechaniciancombat.TkGunServer.PState.class,long.class);
        tick.setAccessible(true); tick.invoke(null,player,st,level.getGameTime());
        helper.assertTrue(st.state==com.mechaniciancombat.TkGun.State.ROLL && !st.landingRollArmed,
                "landing must consume the armed roll");
        st.state=com.mechaniciancombat.TkGun.State.SKILL_JUMP; st.airborneDisqualified=true;
        helper.assertTrue(com.mechaniciancombat.TkGunServer.applyAirborneLandingDamageReduction(player,7)==7 && !st.landingRollArmed,
                "skill jumps must not arm an ordinary landing roll");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void veritasMeleeHitsAtFrameAndQueuesCombo(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "veritas_melee_test"));
        var pos = helper.absolutePos(new BlockPos(8, 4, 8));
        player.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0); player.setOnGround(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.VERITAS_ET_JUSTITIA.get()));
        var target = EntityType.ZOMBIE.create(level);
        target.moveTo(pos.getX(), pos.getY(), pos.getZ()+2, 0, 0); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.setHealth(100); level.addFreshEntity(target);
        var st = com.mechaniciancombat.TkGunServer.state(player); long now = level.getGameTime();
        var server = com.mechaniciancombat.TkGunServer.class;
        var input = server.getDeclaredMethod("handleMeleeInput", net.minecraft.server.level.ServerPlayer.class, boolean.class, boolean.class);
        var tick = server.getDeclaredMethod("tickMelee", net.minecraft.server.level.ServerPlayer.class,
                com.mechaniciancombat.TkGunServer.PState.class, long.class);
        input.setAccessible(true); tick.setAccessible(true);
        input.invoke(null, player, true, false);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.MELEE && st.meleeCombo == 1, "first press must start attack one");
        st.stateStarted = now - 3;
        tick.invoke(null, player, st, now);
        helper.assertTrue(target.getHealth() == 100, "attack one damage arrived too early");
        st.stateStarted = now - 4;
        tick.invoke(null, player, st, now);
        helper.assertTrue(target.getHealth() == 91, "attack one must deal nine damage at tick four; actual health=" + target.getHealth() + " armor=" + target.getArmorValue());
        target.invulnerableTime = 0;
        tick.invoke(null, player, st, now);
        helper.assertTrue(target.getHealth() == 91, "one attack must not damage a target twice");
        input.invoke(null, player, false, false);
        input.invoke(null, player, true, false);
        helper.assertTrue(st.meleeQueued, "second press after the hit frame must queue the next attack");
        target.discard(); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void silentStormLaunchesDuringSprintAndSlide(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "silent_launch_states"));
        var pos = helper.absolutePos(new BlockPos(8, 8, 8));
        player.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.ABYSSAL_ANCIENT_CANNON.get()));
        var st = com.mechaniciancombat.TkGunServer.state(player);
        var server = com.mechaniciancombat.TkGunServer.class;
        var start = server.getDeclaredMethod("startGunJumpAction", net.minecraft.server.level.ServerPlayer.class);
        var tickSlide = server.getDeclaredMethod("tickSlide", net.minecraft.server.level.ServerPlayer.class,
                com.mechaniciancombat.TkGunServer.PState.class, long.class);
        start.setAccessible(true); tickSlide.setAccessible(true);
        long now = level.getGameTime();
        st.state = com.mechaniciancombat.TkGun.State.IDLE;
        player.setOnGround(true); player.setSprinting(true);
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.SKILL_JUMP,
                "ordinary sprint must launch without a separate dodge boost");
        st.state = com.mechaniciancombat.TkGun.State.SLIDE;
        st.slideUntil = now + 10; st.slideMeasuring = true; st.slideDirX = 1; st.slideDirZ = 0;
        st.dashUntil = 0; st.speedBoostUntil = 0; player.setOnGround(false); player.setSprinting(false);
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.SKILL_JUMP && st.slideUntil == 0 && !st.slideMeasuring,
                "an active slide must launch immediately and cancel remaining slide movement");
        var launched = player.getDeltaMovement();
        tickSlide.invoke(null, player, st, now + 1);
        helper.assertTrue(player.getDeltaMovement().equals(launched), "slide tick must not overwrite launch velocity");
        st.state = com.mechaniciancombat.TkGun.State.SLIDE_READY;
        player.setOnGround(true);
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.SKILL_JUMP,
                "slide recovery must also allow immediate launch");
        st.state = com.mechaniciancombat.TkGun.State.ROLL_TO_IDLE;
        st.dashUntil = now + 10; player.setOnGround(true);
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.SKILL_JUMP,
                "post-roll recovery with active acceleration must launch immediately");
        st.state = com.mechaniciancombat.TkGun.State.IDLE;
        st.dashUntil = 0; st.speedBoostUntil = 0; player.setOnGround(true); player.setSprinting(false);
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.IDLE,
                "ordinary standing must not launch");
        player.setSprinting(true); player.setOnGround(false);
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.IDLE,
                "airborne sprint without a slide must not grant another launch");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void silentStormRequiresBoostAndQueuesFall(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "silent_storm_test"));
        var pos = helper.absolutePos(new BlockPos(8, 8, 8));
        player.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        player.setOnGround(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.ABYSSAL_ANCIENT_CANNON.get()));
        var st = com.mechaniciancombat.TkGunServer.state(player);
        long now = level.getGameTime();
        var server = com.mechaniciancombat.TkGunServer.class;
        var start = server.getDeclaredMethod("startSilentStormJump", net.minecraft.server.level.ServerPlayer.class);
        var queue = server.getDeclaredMethod("queueSilentStormFall", net.minecraft.server.level.ServerPlayer.class,
                com.mechaniciancombat.TkGunServer.PState.class, long.class);
        var tick = server.getDeclaredMethod("tickSilentStorm", net.minecraft.server.level.ServerPlayer.class,
                com.mechaniciancombat.TkGunServer.PState.class, long.class);
        start.setAccessible(true); queue.setAccessible(true); tick.setAccessible(true);
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.IDLE, "jump must require acceleration");
        st.dashUntil = now + 30;
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.SKILL_JUMP && st.dashUntil == 0,
                "jump must consume boost");
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - 1.11D) < .00001D, "launch vertical speed changed");
        queue.invoke(null, player, st, now);
        helper.assertTrue(st.fallQueued && st.state == com.mechaniciancombat.TkGun.State.SKILL_JUMP,
                "upward fall input must be queued");
        player.setDeltaMovement(0, -.1, 1);
        tick.invoke(null, player, st, now + 1);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.FALL_ATTACK && st.fallSlowUntil == now + 6,
                "queued attack must begin at descent with five slow ticks");
        var fall = new net.minecraftforge.event.entity.living.LivingFallEvent(player, 12, 1);
        com.mechaniciancombat.TkGunServer.onSkillFall(fall);
        helper.assertTrue(fall.isCanceled(), "fall attack must prevent fall damage");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fortressInputValidatesAndCharges(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "fortress_test"));
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        player.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        player.setOnGround(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        var st = com.mechaniciancombat.TkGunServer.state(player);
        var start = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("startFortressSkill", net.minecraft.server.level.ServerPlayer.class);
        start.setAccessible(true);
        var attack = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("handleFortressSkillAttack",
                net.minecraft.server.level.ServerPlayer.class, net.minecraft.world.phys.Vec3.class);
        attack.setAccessible(true);
        st.deployed = true;
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.IDLE, "focus must block fortress start");
        st.deployed = false;
        start.invoke(null, player);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.FORTRESS_SKILL_LOAD, "grounded bowgun should start load");
        helper.assertTrue(st.stateUntil - st.stateStarted == 19, "load must last 19 ticks");
        st.state = com.mechaniciancombat.TkGun.State.FORTRESS_SKILL_GATHER;
        st.stateStarted = level.getGameTime() - 9;
        helper.assertTrue((Boolean) attack.invoke(null, player, new net.minecraft.world.phys.Vec3(0, 0, 1)), "charge click must be consumed");
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.FORTRESS_SKILL_GATHER, "early click must not release charge");
        st.stateStarted = level.getGameTime() - 20;
        attack.invoke(null, player, new net.minecraft.world.phys.Vec3(0, 0, 1));
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.FORTRESS_SKILL_HIT && st.fortressLevel == 2,
                "second charge level must enter hit state");
        helper.assertTrue(st.fortressReadyAt == level.getGameTime() + 600, "hit must start 600 tick cooldown");
        st.state = com.mechaniciancombat.TkGun.State.FORTRESS_SKILL_GATHER;
        st.stateStarted = level.getGameTime();
        var fortressTick = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("tickFortressSkill",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class, long.class);
        fortressTick.setAccessible(true);
        fortressTick.invoke(null, player, st, level.getGameTime() + 39);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.FORTRESS_SKILL_GATHER, "automatic release must not occur before tick 40");
        fortressTick.invoke(null, player, st, level.getGameTime() + 40);
        helper.assertTrue(st.state == com.mechaniciancombat.TkGun.State.FORTRESS_SKILL_HIT && st.fortressLevel == 3,
                "full charge must release automatically at tick 40");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fortressChargeAndRetreatFollowR611(GameTestHelper helper) {
        helper.assertTrue(com.mechaniciancombat.FortressSkillRules.chargeLevel(9) == 0, "charge must not release before tick 10");
        helper.assertTrue(com.mechaniciancombat.FortressSkillRules.chargeLevel(10) == 1, "first charge level starts at tick 10");
        helper.assertTrue(com.mechaniciancombat.FortressSkillRules.chargeLevel(20) == 2, "second charge level starts at tick 20");
        helper.assertTrue(com.mechaniciancombat.FortressSkillRules.chargeLevel(30) == 3, "third charge level starts at tick 30");
        helper.assertTrue(com.mechaniciancombat.FortressSkillRules.damage(1) == 10.0F
                && com.mechaniciancombat.FortressSkillRules.damage(2) == 15.0F
                && com.mechaniciancombat.FortressSkillRules.damage(3) == 22.5F, "charge damage changed");
        double before = 0;
        for (int i = 0; i <= 28; i++) {
            double distance = com.mechaniciancombat.FortressSkillRules.retreatDistance(i);
            helper.assertTrue(distance >= before && distance <= 5.00001D, "retreat distance must increase smoothly within five blocks");
            before = distance;
        }
        helper.assertTrue(Math.abs(before - 5.0D) < 1.0E-6D, "retreat must cover five blocks");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fortressKnockbackDamagesPathOnlyOnce(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "fortress_push"));
        var origin = helper.absolutePos(new BlockPos(2, 5, 2));
        owner.moveTo(origin.getX() - 4, origin.getY(), origin.getZ());
        var target = EntityType.ZOMBIE.create(level);
        target.moveTo(origin.getX(), origin.getY(), origin.getZ());
        target.setNoAi(true);
        target.setNoGravity(true);
        level.addFreshEntity(target);
        var bystander = EntityType.IRON_GOLEM.create(level);
        bystander.moveTo(origin.getX() + 3, origin.getY() + 2.4, origin.getZ());
        bystander.setNoAi(true);
        bystander.setNoGravity(true);
        level.addFreshEntity(bystander);
        Class<?> service;
        try { service = Class.forName("com.mechaniciancombat.FortressSkillKnockbackService"); }
        catch (ClassNotFoundException missing) { helper.fail("fortress knockback service is missing"); return; }
        var start = service.getMethod("start", net.minecraft.server.level.ServerPlayer.class,
                net.minecraft.world.entity.Entity.class, net.minecraft.world.phys.Vec3.class);
        var tick = service.getMethod("tick", net.minecraft.server.MinecraftServer.class);
        start.invoke(null, owner, target, new net.minecraft.world.phys.Vec3(1, 0, 0));
        for (int i = 0; i < 4; i++) tick.invoke(null, level.getServer());
        float afterFirstHit = bystander.getHealth();
        helper.assertTrue(afterFirstHit < bystander.getMaxHealth(), "path entity must receive damage");
        for (int i = 0; i < 11; i++) tick.invoke(null, level.getServer());
        helper.assertTrue(bystander.getHealth() == afterFirstHit, "each path entity must be damaged only once");
        helper.assertTrue(Math.abs(target.getX() - origin.getX() - 15) < 0.001, "knockback must travel 15 blocks");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void golemLandingProtectsOwnersPet(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(8, 3, 8));
        var owner = UUID.randomUUID();
        var golem = ModRegistries.MOLTEN_GOLEM.get().create(level);
        golem.moveTo(position.getX(), position.getY(), position.getZ());
        golem.setSummoner(owner);
        var wolf = EntityType.WOLF.create(level);
        wolf.setOwnerUUID(owner);
        wolf.moveTo(position.getX() + 1, position.getY(), position.getZ());
        wolf.setNoAi(true);
        level.addFreshEntity(wolf);
        var zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(position.getX() + 1, position.getY(), position.getZ() + 1);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        float wolfHealth = wolf.getHealth();
        float zombieHealth = zombie.getHealth();
        for (int i = 0; i < 10; i++) golem.tick();
        helper.assertTrue(wolf.getHealth() == wolfHealth, "landing damaged owner's pet");
        helper.assertTrue(zombie.getHealth() < zombieHealth, "landing did not damage enemy");
        wolf.discard();
        zombie.discard();
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rollDirectionPacketAndFallback(GameTestHelper helper) throws Exception {
        var packet = new com.mechaniciancombat.net.GunRollPayload(-1, 1, 90.0F);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.mechaniciancombat.net.GunRollPayload.CODEC.encoder().accept(packet, buffer);
            helper.assertTrue(packet.equals(com.mechaniciancombat.net.GunRollPayload.CODEC.decoder().apply(buffer)),
                    "roll input must survive the network codec");
        } finally { buffer.release(); }
        helper.assertTrue(!new com.mechaniciancombat.net.GunRollPayload(2, 0, 0).valid()
                && !new com.mechaniciancombat.net.GunRollPayload(0, 0, Float.NaN).valid(),
                "invalid roll input must be rejected");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("5157a08b-bd1b-40c9-a1d1-f6a26aa2e46e"), "roll_direction"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get()));
        var state = com.mechaniciancombat.TkGunServer.state(player);
        var requested = com.mechaniciancombat.RollDirection.fromInput(-1, 0, 0);
        state.rollIntentX = requested.x();
        state.rollIntentZ = requested.z();
        player.setDeltaMovement(0, 0.2, 0.4); // Inertia points forward; S must still roll backwards.
        var start = com.mechaniciancombat.TkGunServer.class.getDeclaredMethod("startFallbackRoll",
                net.minecraft.server.level.ServerPlayer.class, com.mechaniciancombat.TkGunServer.PState.class, long.class);
        start.setAccessible(true);
        start.invoke(null, player, state, helper.getLevel().getGameTime());
        var velocity = player.getDeltaMovement();
        helper.assertTrue(Math.abs(velocity.x) < 1.0E-6 && velocity.z < -0.7 && Math.abs(velocity.y - 0.2) < 1.0E-6,
                "fallback roll must follow input rather than existing velocity and preserve vertical speed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialHipReloadUsesAvailableRounds(GameTestHelper helper) {
        for (int available = 0; available <= 8; available++) {
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(("partial-reload-" + available).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "partial_reload_" + available));
            var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
            var ring = net.minecraft.core.NonNullList.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE, net.minecraft.world.item.ItemStack.EMPTY);
            if (available > 0) ring.set(0, new net.minecraft.world.item.ItemStack(com.mechaniciancombat.TkGunServer.BOLT, Math.min(available, 2)));
            if (available > 2) ring.set(2, new net.minecraft.world.item.ItemStack(com.mechaniciancombat.TkGunServer.BOLT, available - 2));
            com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0, helper.getLevel().registryAccess());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
            com.mechaniciancombat.TkGunServer.reload(player);
            var state = com.mechaniciancombat.TkGunServer.state(player);
            int loaded = Math.min(6, available);
            int reserve = com.mechaniciancombat.item.GunAmmoData.read(gun, helper.getLevel().registryAccess())
                    .stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            helper.assertTrue(state.mag == 6 - loaded && reserve == available - loaded,
                    "incorrect partial reload for available=" + available);
            helper.assertTrue(com.mechaniciancombat.item.GunAmmoData.savedMagazineCount(gun) == 6 - loaded,
                    "partial magazine must be persisted for available=" + available);
            helper.assertTrue((state.reloadUntil > helper.getLevel().getGameTime()) == (loaded > 0),
                    "reload animation must start only when rounds are loaded");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialReloadKeepsLoadedRoundsAndAmmoData(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("695da7df-9ea0-43bd-915a-bb5109ce2963"), "partial_topup"));
        var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var bolt = new net.minecraft.world.item.ItemStack(com.mechaniciancombat.TkGunServer.BOLT, 1);
        bolt.getOrCreateTag().putString("tkgun_test_marker", "preserved");
        var ring = net.minecraft.core.NonNullList.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE, net.minecraft.world.item.ItemStack.EMPTY);
        ring.set(0, bolt);
        com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0, helper.getLevel().registryAccess());
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(gun, 4, "tkgun:bolt"); // 2 already loaded
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        com.mechaniciancombat.TkGunServer.reload(player);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        helper.assertTrue(state.mag == 3 && com.mechaniciancombat.item.GunAmmoData.savedMagazineCount(gun) == 3,
                "one spare round must top the existing two up to three");
        helper.assertTrue(com.mechaniciancombat.item.GunAmmoData.read(gun, helper.getLevel().registryAccess()).get(0).isEmpty(),
                "the one loaded round must be removed from the ring");
        var afterReload = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("0f5fa385-d0da-4b0a-9d28-22a1f28667dc"), "partial_rejoin"));
        afterReload.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun.copy());
        com.mechaniciancombat.TkGunServer.tryFire(afterReload, false);
        var restored = com.mechaniciancombat.TkGunServer.state(afterReload);
        helper.assertTrue(restored.magAmmoStack.hasTag()
                        && "preserved".equals(restored.magAmmoStack.getTag().getString("tkgun_test_marker")),
                "loaded ammunition NBT must survive when the reserve is empty and the gun is reloaded from disk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void focusReloadAcceptsPartialReserve(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("38c8bf3a-dc14-4f10-9e7c-06c4eb924858"), "partial_focus"));
        var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var ring = net.minecraft.core.NonNullList.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE, net.minecraft.world.item.ItemStack.EMPTY);
        ring.set(0, new net.minecraft.world.item.ItemStack(com.mechaniciancombat.TkGunServer.BOLT, 3));
        com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0, helper.getLevel().registryAccess());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        state.deployed = true;
        com.mechaniciancombat.TkGunServer.reload(player);
        helper.assertTrue(state.mag == 3 && com.mechaniciancombat.item.GunAmmoData.savedMagazineCount(gun) == 3,
                "focus reload must persist the three rounds available");
        helper.assertTrue(state.reloadUntil > helper.getLevel().getGameTime(), "partial focus reload must start");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialReloadSwitchReturnsOldRounds(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("f0cc64c2-b8de-4ac4-8668-f2703f07355c"), "partial_switch"));
        var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var ring = net.minecraft.core.NonNullList.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE, net.minecraft.world.item.ItemStack.EMPTY);
        ring.set(0, new net.minecraft.world.item.ItemStack(ModRegistries.BULLETS.get(0).get(), 3));
        com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0, helper.getLevel().registryAccess());
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(gun, 4, "tkgun:bolt");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        com.mechaniciancombat.TkGunServer.reload(player);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        int refunded = com.mechaniciancombat.item.GunAmmoData.read(gun, helper.getLevel().registryAccess()).stream()
                .filter(s -> s.is(com.mechaniciancombat.TkGunServer.BOLT)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
        helper.assertTrue(state.mag == 3 && "tkgun:cutting_bolt".equals(state.magAmmo) && refunded == 2,
                "switch must load three new rounds and return both old rounds");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ammoSwitchRollsBackWhenOldRoundsCannotFit(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("6512020e-9997-46b6-adba-fb4e9bf3d17c"), "partial_rollback"));
        var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var cutting = ModRegistries.BULLETS.get(0).get();
        var ring = net.minecraft.core.NonNullList.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE, net.minecraft.world.item.ItemStack.EMPTY);
        ring.set(0, new net.minecraft.world.item.ItemStack(cutting, 7));
        for (int slot = 1; slot < ring.size(); slot++) ring.set(slot, new net.minecraft.world.item.ItemStack(cutting, 64));
        com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0, helper.getLevel().registryAccess());
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(gun, 4, "tkgun:bolt");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        com.mechaniciancombat.TkGunServer.reload(player);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        var after = com.mechaniciancombat.item.GunAmmoData.read(gun, helper.getLevel().registryAccess());
        helper.assertTrue(state.mag == 4 && "tkgun:bolt".equals(state.magAmmo)
                        && after.get(0).getCount() == 7 && after.get(1).getCount() == 64,
                "failed switch must restore the exact reserve and keep old rounds loaded");
        helper.assertTrue(state.reloadUntil <= helper.getLevel().getGameTime(), "failed switch must not start reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rollRecoveryAllowsAim(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.fromString("a9877273-10c5-4f9d-b9a8-cea845a4ba43"), "roll_aim_test"));
        var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        long now = level.getGameTime();
        state.dashUntil = now + 60;
        state.state = com.mechaniciancombat.TkGun.State.ROLL;
        ModRegistries.BOWGUN.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(!player.isUsingItem(), "aim must be blocked during the actual roll");
        state.state = com.mechaniciancombat.TkGun.State.ROLL_TO_IDLE;
        ModRegistries.BOWGUN.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "aim must be available in roll recovery while dash is active");
        player.stopUsingItem();
        state.state = com.mechaniciancombat.TkGun.State.IDLE;
        ModRegistries.BOWGUN.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "aim must stay available after roll recovery during dash");
        player.stopUsingItem();
        state.state = com.mechaniciancombat.TkGun.State.SLIDE;
        ModRegistries.BOWGUN.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(!player.isUsingItem(), "slide must still block aiming");
        state.state = com.mechaniciancombat.TkGun.State.IDLE;
        state.aimLockUntil = now + 20;
        ModRegistries.BOWGUN.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(!player.isUsingItem(), "the existing slide recovery aim lock must remain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rollRecoveryAllowsFire(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.fromString("f8202522-0c6d-4ca2-ac49-a971e159144c"), "roll_fire_test"));
        var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var ring = net.minecraft.core.NonNullList.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE,
                net.minecraft.world.item.ItemStack.EMPTY);
        ring.set(0, new net.minecraft.world.item.ItemStack(com.mechaniciancombat.TkGunServer.BOLT, 4));
        com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0, level.registryAccess());
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(gun, 4, "tkgun:bolt");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        long now = level.getGameTime();
        state.lastShotAt = now - 1;
        state.state = com.mechaniciancombat.TkGun.State.ROLL;
        state.aimLockUntil = now + 20;
        state.dashUntil = now + 60;
        com.mechaniciancombat.TkGunServer.tryFire(player, false);
        helper.assertTrue(state.shotQueue.isEmpty() && state.mag == 4,
                "fire must be blocked during the roll itself");
        state.lastFireTick = now - 1;
        state.state = com.mechaniciancombat.TkGun.State.ROLL_TO_IDLE;
        com.mechaniciancombat.TkGunServer.tryFire(player, false);
        helper.assertTrue(state.shotQueue.size() == 1 && state.mag == 5,
                "roll recovery must accept a shot even while aim lock and dash remain active");
        com.mechaniciancombat.TkGunServer.tryFire(player, false);
        helper.assertTrue(state.shotQueue.size() == 1 && state.mag == 5,
                "a duplicate recovery trigger must not consume another round");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hipFireDoesNotRequireAim(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.fromString("2767823f-bd47-40ec-ad2b-672a412472c8"), "hip_fire_test"));
        var gun = new net.minecraft.world.item.ItemStack(ModRegistries.BOWGUN.get());
        var ring = net.minecraft.core.NonNullList.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE,
                net.minecraft.world.item.ItemStack.EMPTY);
        ring.set(0, new net.minecraft.world.item.ItemStack(com.mechaniciancombat.TkGunServer.BOLT, 4));
        com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0, level.registryAccess());
        com.mechaniciancombat.item.GunAmmoData.saveMagazine(gun, 4, "tkgun:bolt");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        var state = com.mechaniciancombat.TkGunServer.state(player);
        long now = level.getGameTime();
        state.lastShotAt = now - 1;
        helper.assertTrue(!player.isUsingItem(), "test player must not be aiming");
        com.mechaniciancombat.TkGunServer.tryFire(player, false);
        helper.assertTrue(state.shotQueue.size() == 1 && state.mag == 5,
                "hip fire must enqueue one shot and consume one round without aiming");
        helper.assertTrue(state.shotCycle == com.mechaniciancombat.TkGun.SHOT_CYCLE_TICKS,
                "hip fire must use the normal firing interval");
        com.mechaniciancombat.TkGunServer.tryFire(player, false);
        helper.assertTrue(state.shotQueue.size() == 1 && state.mag == 5,
                "duplicate trigger in the same tick must not consume another round");
        state.shotQueue.clear();
        state.shotAnimating = false;
        state.lastFireTick = now - 1;
        state.lastShotAt = now - 1;
        ModRegistries.BOWGUN.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "test player must be aiming");
        com.mechaniciancombat.TkGunServer.tryFire(player, false);
        helper.assertTrue(state.shotQueue.size() == 1 && state.mag == 6,
                "aimed fire must still enqueue one shot");
        helper.assertTrue(state.shotCycle == com.mechaniciancombat.TkGun.SHOT_CYCLE_TICKS,
                "aimed and hip fire must use the same firing interval");
        player.stopUsingItem();
        state.shotQueue.clear();
        state.shotAnimating = false;
        state.overheatUntil = now + 20;
        state.lastFireTick = now - 1;
        com.mechaniciancombat.TkGunServer.tryFire(player, false);
        helper.assertTrue(state.shotQueue.isEmpty() && state.mag == 6,
                "overheat must still block hip fire");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void r396ContentIsRegistered(GameTestHelper helper) {
        helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(id("molten_golem")),
                "r396 molten golem entity is missing");
        for (String item : new String[]{"summon_antenna", "molten_golem_spawn_egg",
                "backpack_cooldown_reset", "entity_removal_tool"}) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(item)), "Missing r396 item " + item);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void r396StatePacketsRoundTrip(GameTestHelper helper) {
        var expected = new GunStatePayload(123, (byte) 7, 11, 75, 3, 6, true, true,
                19, true, 60, 20, 100, "tkgun:explosive_bolt", 39, true, false, true, false, true, true, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GunStatePayload.CODEC.encoder().accept(expected, buf);
            helper.assertTrue(expected.equals(GunStatePayload.CODEC.decoder().apply(buf)),
                    "r396 state fields were lost or reordered");
            helper.assertTrue(buf.readableBytes() == 0, "state packet left unread bytes");
            for (int phase = 5; phase <= 9; phase++) {
                var status = new CombatBackpackStatusPayload(123, 456, phase, 0, 60,
                        3, 0, 28, false);
                buf.clear();
                CombatBackpackStatusPayload.CODEC.encoder().accept(status, buf);
                helper.assertTrue(status.equals(CombatBackpackStatusPayload.CODEC.decoder().apply(buf)),
                        "summon phase did not round trip: " + phase);
            }
        } finally { buf.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void golemKeepsOwnerAndAttackStateOnReload(GameTestHelper helper) {
        var level = helper.getLevel();
        MoltenGolemEntity golem = ModRegistries.MOLTEN_GOLEM.get().create(level);
        helper.assertTrue(golem != null && golem.getMaxHealth() == 400.0F, "golem attributes must be 400 HP");
        UUID owner = UUID.randomUUID();
        golem.setSummoner(owner);
        CompoundTag saved = new CompoundTag();
        golem.saveWithoutId(saved);
        helper.assertTrue(saved.getInt("SummonTicks") == 70, "landing duration changed");
        saved.putInt("SummonTicks", 0);
        saved.putString("AttackKind", "ATK3");
        saved.putInt("AttackElapsed", 38);
        saved.putBoolean("AttackDamageDone", true);
        saved.putInt("Atk2Cooldown", 100);
        saved.putInt("Atk3Cooldown", 400);
        MoltenGolemEntity restored = ModRegistries.MOLTEN_GOLEM.get().create(level);
        restored.load(saved);
        CompoundTag after = new CompoundTag();
        restored.saveWithoutId(after);
        helper.assertTrue(restored.isSummonedBy(owner), "summoner UUID was lost");
        helper.assertTrue(restored.isCombatAttacking(), "attack state was lost");
        for (String key : new String[]{"Owner", "SummonTicks", "AttackKind", "AttackElapsed",
                "AttackDamageDone", "Atk2Cooldown", "Atk3Cooldown", "VisualState"}) {
            helper.assertTrue(saved.get(key).equals(after.get(key)), "NBT changed on reload: " + key);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void golemThirdAttackDamagesOnceAtFrame38(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(8, 3, 8));
        MoltenGolemEntity golem = ModRegistries.MOLTEN_GOLEM.get().create(level);
        golem.moveTo(position.getX(), position.getY(), position.getZ(), 0, 0);
        CompoundTag ready = new CompoundTag();
        golem.saveWithoutId(ready);
        ready.putInt("SummonTicks", 0);
        golem.load(ready);
        var target = EntityType.ZOMBIE.create(level);
        target.moveTo(position.getX(), position.getY(), position.getZ() + 2, 0, 0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(1000);
        target.setNoAi(true);
        level.addFreshEntity(target);
        golem.setTarget(target);
        helper.assertTrue(golem.doHurtTarget(target), "golem did not begin its first attack");
        for (int tick = 0; tick < 37; tick++) golem.tick();
        helper.assertTrue(target.getHealth() == 1000, "damage arrived before frame 38");
        golem.tick();
        helper.assertTrue(target.getHealth() == 985, "ATK3 must deal 15 damage at frame 38");
        CompoundTag saved = new CompoundTag();
        golem.saveWithoutId(saved);
        MoltenGolemEntity restored = ModRegistries.MOLTEN_GOLEM.get().create(level);
        restored.load(saved);
        restored.setTarget(target);
        for (int tick = 0; tick < 19; tick++) {
            target.invulnerableTime = 0;
            restored.tick();
        }
        helper.assertTrue(target.getHealth() == 985, "reloaded attack applied damage twice");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void antennaSummonsAndReusesOwnedGolem(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "r396_summoner"));
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        owner.moveTo(pos.getX(), pos.getY(), pos.getZ(), 180, 0);
        var backpack = new net.minecraft.world.item.ItemStack(ModRegistries.COMBAT_BACKPACK.get());
        com.mechaniciancombat.item.BackpackUpgradeData.installCore(backpack);
        com.mechaniciancombat.item.BackpackUpgradeData.setLauncherType(backpack,
                com.mechaniciancombat.item.BackpackUpgradeData.LauncherType.SUMMON_ANTENNA);
        com.mechaniciancombat.item.BackpackUpgradeData.setGolemCapsule(backpack, true);
        var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(owner).resolve().orElseThrow();
        inventory.setEquippedCurio("back", 0, backpack);
        helper.assertTrue(com.mechaniciancombat.item.CombatBackpackItem.equippedStack(owner) == backpack,
                "summon antenna must use the actual equipped Curios backpack");
        var eye = owner.getEyePosition();
        var payload = new com.mechaniciancombat.net.CombatBackpackFirePayload(pos.getX(), pos.getY(), pos.getZ(),
                eye.x, eye.y, eye.z, 180, 0, 0.01, 0.01);
        var server = com.mechaniciancombat.TkGunServer.class;
        var stateMethod = server.getDeclaredMethod("backpackState", net.minecraft.server.level.ServerPlayer.class, long.class);
        stateMethod.setAccessible(true);
        var action = server.getDeclaredMethod("handleBackpackAction", net.minecraft.server.level.ServerPlayer.class,
                com.mechaniciancombat.net.CombatBackpackFirePayload.class);
        action.setAccessible(true);
        long now = level.getGameTime();
        Object state = stateMethod.invoke(null, owner, now);
        var chargeClass = state.getClass();
        var ready = chargeClass.getDeclaredField("summonReadyUntil"); ready.setAccessible(true);
        var cast = chargeClass.getDeclaredField("summonCastUntil"); cast.setAccessible(true);
        var cooldown = chargeClass.getDeclaredField("cooldownUntil"); cooldown.setAccessible(true);
        var tick = server.getDeclaredMethod("tickSummonAntenna", net.minecraft.server.level.ServerPlayer.class, chargeClass, long.class);
        tick.setAccessible(true);
        com.mechaniciancombat.TkGunServer.clearBackpackCooldown(owner);
        action.invoke(null, owner, payload);
        helper.assertTrue(ready.getLong(state) == now + 20, "antenna preparation must last 20 ticks");
        // Advance the controller clock without changing the shared GameTest world clock.
        ready.setLong(state, Math.max(1, now - 1));
        action.invoke(null, owner, payload);
        helper.assertTrue(cast.getLong(state) == now + 60, "antenna cast must last 60 ticks");
        tick.invoke(null, owner, state, now + 60);
        java.util.List<MoltenGolemEntity> owned = new java.util.ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof MoltenGolemEntity golem && golem.isSummonedBy(owner.getUUID())) owned.add(golem);
        }
        helper.assertTrue(owned.size() == 1, "antenna must spawn exactly one owned golem");
        var first = owned.get(0);
        helper.assertTrue(cooldown.getLong(state) == now + 60 + 300, "successful summon must enter 300-tick cooldown");
        first.setHealth(200);
        com.mechaniciancombat.TkGunServer.clearBackpackCooldown(owner);
        action.invoke(null, owner, payload);
        ready.setLong(state, Math.max(1, now - 1));
        action.invoke(null, owner, payload);
        tick.invoke(null, owner, state, now + 60);
        int count = 0;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof MoltenGolemEntity golem && golem.isSummonedBy(owner.getUUID())) {
                count++;
                helper.assertTrue(golem.getUUID().equals(first.getUUID()), "resummon replaced the existing golem");
            }
        }
        helper.assertTrue(count == 1 && first.getHealth() == 280, "resummon must reuse and heal by 20% max HP");
        first.discard();
        helper.succeed();
    }
    private static ResourceLocation id(String name) { return new ResourceLocation("tkgun", name); }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void autoTurretReacquiresAndCompletesCooldown(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "turret_cycle"));
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        owner.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        var backpack = new net.minecraft.world.item.ItemStack(ModRegistries.COMBAT_BACKPACK.get());
        com.mechaniciancombat.item.BackpackUpgradeData.installCore(backpack);
        com.mechaniciancombat.item.BackpackUpgradeData.setLauncherType(backpack,
                com.mechaniciancombat.item.BackpackUpgradeData.LauncherType.AUTO);
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(owner).resolve().orElseThrow();
        curios.setEquippedCurio("back", 0, backpack);
        owner.getInventory().offhand.set(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET, 16));
        var first = EntityType.PIG.create(level);
        var second = EntityType.PIG.create(level);
        first.moveTo(pos.getX(), pos.getY(), pos.getZ() + 3, 0, 0);
        second.moveTo(pos.getX() + 2, pos.getY(), pos.getZ() + 6, 0, 0);
        first.setNoAi(true); second.setNoAi(true);
        level.addFreshEntity(first); level.addFreshEntity(second);
        var server = com.mechaniciancombat.TkGunServer.class;
        var action = server.getDeclaredMethod("handleBackpackAction", net.minecraft.server.level.ServerPlayer.class,
                com.mechaniciancombat.net.CombatBackpackFirePayload.class);
        action.setAccessible(true);
        var stateMethod = server.getDeclaredMethod("backpackState", net.minecraft.server.level.ServerPlayer.class, long.class);
        stateMethod.setAccessible(true);
        long now = level.getGameTime();
        Object state = stateMethod.invoke(null, owner, now);
        var cls = state.getClass();
        var tick = server.getDeclaredMethod("tickAutoBackpack", net.minecraft.server.level.ServerPlayer.class, cls, long.class);
        tick.setAccessible(true);
        var charges = cls.getDeclaredField("charges"); charges.setAccessible(true);
        var active = cls.getDeclaredField("autoActive"); active.setAccessible(true);
        var target = cls.getDeclaredField("targetEntityId"); target.setAccessible(true);
        var cooldown = cls.getDeclaredField("cooldownUntil"); cooldown.setAccessible(true);
        var updateView = server.getDeclaredMethod("updateBackpackAim", net.minecraft.server.level.ServerPlayer.class, cls,
                double.class, double.class, double.class, double.class, double.class, long.class);
        var visible = server.getDeclaredMethod("autoTargetVisible", net.minecraft.server.level.ServerPlayer.class,
                net.minecraft.world.entity.LivingEntity.class, cls, long.class);
        updateView.setAccessible(true); visible.setAccessible(true);
        var eye = owner.getEyePosition();
        var payload = new com.mechaniciancombat.net.CombatBackpackFirePayload(eye.x, eye.y, eye.z + 0.5,
                eye.x, eye.y, eye.z, 0, 0, 1, 1);
        try {
            action.invoke(null, owner, payload);
            helper.assertTrue(charges.getInt(state) == 9 && target.getInt(state) == first.getId(),
                    "first shot must select the nearest target and consume one charge");
            updateView.invoke(null, owner, state, eye.x, eye.y, eye.z, 180.0D, 0.0D, now);
            helper.assertTrue(!(Boolean) visible.invoke(null, owner, first, state, now), "target behind camera must not be visible");
            updateView.invoke(null, owner, state, eye.x, eye.y, eye.z, 0.0D, 0.0D, now);
            helper.assertTrue((Boolean) visible.invoke(null, owner, first, state, now), "target in front of camera must be visible");
            helper.assertTrue(!(Boolean) visible.invoke(null, owner, first, state, now + 21), "stale camera data must not select a target");
            first.discard();
            tick.invoke(null, owner, state, now + 1);
            helper.assertTrue(target.getInt(state) == second.getId() && charges.getInt(state) == 9,
                    "removed target must be replaced without an early shot");

            for (int shot = 1; shot < 10; shot++) {
                long shotTime = now + shot * 20L;
                updateView.invoke(null, owner, state, eye.x, eye.y, eye.z, 0.0D, 0.0D, shotTime);
                tick.invoke(null, owner, state, shotTime);
            }
            helper.assertTrue(charges.getInt(state) == 0 && !active.getBoolean(state)
                    && owner.getOffhandItem().getCount() == 6, "ten shots must consume ten rockets and enter cooldown");
            long end = cooldown.getLong(state);
            helper.assertTrue(end == now + 180 + 200, "cooldown must last 200 ticks");
            tick.invoke(null, owner, state, end - 1);
            helper.assertTrue(charges.getInt(state) == 0, "cooldown must not finish early");
            tick.invoke(null, owner, state, end);
            helper.assertTrue(charges.getInt(state) == 10 && cooldown.getLong(state) == 0,
                    "cooldown must restore all charges at its deadline");
        } finally {
            first.discard(); second.discard();
            curios.setEquippedCurio("back", 0, net.minecraft.world.item.ItemStack.EMPTY);
            owner.getInventory().offhand.set(0, net.minecraft.world.item.ItemStack.EMPTY);
            var clear = server.getDeclaredMethod("clearBackpackState", net.minecraft.server.level.ServerPlayer.class);
            clear.setAccessible(true); clear.invoke(null, owner);
        }
        helper.succeed();
    }
}
