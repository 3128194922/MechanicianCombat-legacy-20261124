package com.mechaniciancombat.registry;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.block.BackpackModificationTableBlock;
import com.mechaniciancombat.block.BackpackModificationTableBlockEntity;
import com.mechaniciancombat.entity.BoltProjectileEntity;
import com.mechaniciancombat.entity.BulletEntity;
import com.mechaniciancombat.entity.MoltenTridentEntity;
import com.mechaniciancombat.entity.WindChargeEntity;
import com.mechaniciancombat.item.BackpackPartItem;
import com.mechaniciancombat.item.BoltItem;
import com.mechaniciancombat.item.BowgunItem;
import com.mechaniciancombat.item.CombatBackpackItem;
import com.mechaniciancombat.item.MoltenTridentItem;
import com.mechaniciancombat.item.WindChargeItem;
import com.mechaniciancombat.screen.AmmoStorageScreenHandler;
import com.mechaniciancombat.screen.BackpackModificationTableScreenHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** All game content is registered through Forge's mod event bus. */
public final class ModRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, TkGun.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, TkGun.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TkGun.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TkGun.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TkGun.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TkGun.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, TkGun.MOD_ID);

    public static final RegistryObject<Block> BACKPACK_TABLE_BLOCK = BLOCKS.register(
            "backpack_modification_table", () -> new BackpackModificationTableBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F)
                            .requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final RegistryObject<BlockItem> BACKPACK_TABLE_ITEM = ITEMS.register(
            "backpack_modification_table", () -> new BlockItem(BACKPACK_TABLE_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<BackpackModificationTableBlockEntity>> BACKPACK_TABLE_ENTITY =
            BLOCK_ENTITIES.register("backpack_modification_table", () -> BlockEntityType.Builder.of(
                    BackpackModificationTableBlockEntity::new, BACKPACK_TABLE_BLOCK.get()).build(null));
    public static final RegistryObject<Block> ARMOR_TABLE_BLOCK = BLOCKS.register(
            "armor_modification_table", () -> new com.mechaniciancombat.block.ArmorModificationTableBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F)
                            .requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final RegistryObject<BlockItem> ARMOR_TABLE_ITEM = ITEMS.register(
            "armor_modification_table", () -> new BlockItem(ARMOR_TABLE_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<BackpackModificationTableScreenHandler>>
            BACKPACK_TABLE_MENU = MENUS.register("backpack_modification_table",
                    () -> net.minecraftforge.common.extensions.IForgeMenuType.create(
                            (id, inventory, data) -> new BackpackModificationTableScreenHandler(
                                    id, inventory, data.readBlockPos())));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<com.mechaniciancombat.screen.ArmorModificationTableScreenHandler>>
            ARMOR_TABLE_MENU = MENUS.register("armor_modification_table",
                    () -> net.minecraftforge.common.extensions.IForgeMenuType.create(
                            (id, inventory, data) -> new com.mechaniciancombat.screen.ArmorModificationTableScreenHandler(
                                    id, inventory, data.readBlockPos())));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<com.mechaniciancombat.screen.RecipeEditorScreenHandler>>
            RECIPE_EDITOR_MENU = MENUS.register("recipe_editor",
                    () -> net.minecraftforge.common.extensions.IForgeMenuType.create(
                            (id, inventory, data) -> new com.mechaniciancombat.screen.RecipeEditorScreenHandler(
                                    id, inventory)));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<AmmoStorageScreenHandler>> AMMO_MENU =
            MENUS.register("ammo_storage", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(
                    (id, inventory, data) -> new AmmoStorageScreenHandler(id, inventory, data)));

    public static final RegistryObject<Item> BACKPACK_CORE = ITEMS.register("backpack_core",
            () -> new BackpackPartItem(BackpackPartItem.Kind.CORE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PORTABLE_LAUNCHER = ITEMS.register("portable_launcher",
            () -> new BackpackPartItem(BackpackPartItem.Kind.LAUNCHER, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> AUTO_PORTABLE_LAUNCHER = ITEMS.register("auto_portable_launcher",
            () -> new BackpackPartItem(BackpackPartItem.Kind.AUTO_LAUNCHER, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SUMMON_ANTENNA = ITEMS.register("summon_antenna",
            () -> new BackpackPartItem(BackpackPartItem.Kind.SUMMON_ANTENNA, new Item.Properties().stacksTo(1)));
    /** 战旗部件（r774）：装到背包后，背包动作在落点插旗。 */
    public static final RegistryObject<Item> WAR_BANNER_ITEM = ITEMS.register("war_banner",
            () -> new BackpackPartItem(BackpackPartItem.Kind.BATTLE_BANNER, new Item.Properties().stacksTo(1)));
    /** 跳跃推进器部件（r774）。 */
    public static final RegistryObject<Item> JUMP_BACKPACK_THRUSTER = ITEMS.register("jump_backpack_thruster",
            () -> new BackpackPartItem(BackpackPartItem.Kind.JUMP_THRUSTER, new Item.Properties().stacksTo(1)));
    /** 导弹舱部件（r774）。 */
    public static final RegistryObject<Item> MISSILE_BOX = ITEMS.register("missile_box",
            () -> new BackpackPartItem(BackpackPartItem.Kind.MISSILE_BOX, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GOLEM_DISPATCH_CAPSULE = ITEMS.register("golem_dispatch_capsule",
            () -> new BackpackPartItem(BackpackPartItem.Kind.GOLEM_CAPSULE, new Item.Properties().stacksTo(1)));
    /** 护甲模块（r7xx）：C1 不死图腾 / C2 左肩甲 / C3 灵魂锁链 / T1 灵魂锚。 */
    public static final RegistryObject<Item> UNDYING_TOTEM_PART = ITEMS.register("undying_totem",
            () -> new com.mechaniciancombat.item.ArmorPartItem(
                    com.mechaniciancombat.item.ArmorPartItem.Kind.C1, new Item.Properties()));
    public static final RegistryObject<Item> PAULDRON_LEFT = ITEMS.register("pauldorn_left",
            () -> new com.mechaniciancombat.item.ArmorPartItem(
                    com.mechaniciancombat.item.ArmorPartItem.Kind.C2, new Item.Properties()));
    public static final RegistryObject<Item> SOUL_CHAIN = ITEMS.register("soul_chain",
            () -> new com.mechaniciancombat.item.ArmorPartItem(
                    com.mechaniciancombat.item.ArmorPartItem.Kind.C3, new Item.Properties()));
    public static final RegistryObject<Item> SOUL_ANCHOR_PART = ITEMS.register("soul_anchor",
            () -> new com.mechaniciancombat.item.ArmorPartItem(
                    com.mechaniciancombat.item.ArmorPartItem.Kind.T1, new Item.Properties()));
    /** 猎矛：右键在地面部署固定炮台。 */
    public static final RegistryObject<Item> HUNTER_SPEAR = ITEMS.register("hunter_spear",
            () -> new com.mechaniciancombat.item.MechanicianHunterSpearItem(
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BACKPACK_COOLDOWN_RESET = ITEMS.register("backpack_cooldown_reset",
            () -> new com.mechaniciancombat.item.BackpackCooldownResetItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ENTITY_REMOVAL_TOOL = ITEMS.register("entity_removal_tool",
            () -> new com.mechaniciancombat.item.EntityRemovalToolItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<EntityType<com.mechaniciancombat.entity.MoltenGolemEntity>> MOLTEN_GOLEM =
            ENTITIES.register("molten_golem", () -> EntityType.Builder.<com.mechaniciancombat.entity.MoltenGolemEntity>of(
                    com.mechaniciancombat.entity.MoltenGolemEntity::new, MobCategory.MISC).sized(1.4F, 2.7F)
                    .build(TkGun.id("molten_golem").toString()));
    public static final RegistryObject<Item> MOLTEN_GOLEM_SPAWN_EGG = ITEMS.register("molten_golem_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(MOLTEN_GOLEM, 0xD7D7D7, 0x6A6A6A, new Item.Properties()));
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TkGun.MOD_ID);
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> GOLEM_SWING = sound("golem_swing");
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> GOLEM_HAMMER = sound("golem_hammer");
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> GOLEM_EXPLOSION = sound("golem_explosion");
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> GOLEM_ELECTRIC = sound("golem_electric");

    public static final RegistryObject<Item> MECHANICIAN_UNDERSUIT = ITEMS.register("mechanician_undersuit",
            () -> new com.mechaniciancombat.item.MechanicianUndersuitItem(new Item.Properties()));
    public static final RegistryObject<Item> MECHANICIAN_CHESTPLATE = ITEMS.register("mechanician_chestplate",
            () -> new com.mechaniciancombat.item.MechanicianArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> MECHANICIAN_LEGGINGS = ITEMS.register("mechanician_leggings",
            () -> new com.mechaniciancombat.item.MechanicianArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> MECHANICIAN_BOOTS = ITEMS.register("mechanician_boots",
            () -> new com.mechaniciancombat.item.MechanicianArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> BOWGUN = ITEMS.register("bowgun",
            () -> new BowgunItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ABYSSAL_ANCIENT_CANNON = ITEMS.register("abyssal_ancient_cannon",
            () -> new BowgunItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> VERITAS_ET_JUSTITIA = ITEMS.register("veritas_et_justitia",
            () -> new BowgunItem(new Item.Properties().stacksTo(1)));
    /** 动能剑：近战型“枪械”，复用枪械状态机与第一人称渲染。 */
    public static final RegistryObject<Item> KINETIC_SWORD = ITEMS.register("kinetic_sword",
            () -> new com.mechaniciancombat.item.KineticSwordItem(new Item.Properties().stacksTo(1)));
    /** 灵魂锚 / 动能剑的第一人称隐藏控制器物品。 */
    public static final RegistryObject<Item> SOUL_ANCHOR_FPA_MODEL = ITEMS.register("soul_anchor_fpa_model",
            () -> new com.mechaniciancombat.item.SoulAnchorFpaItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KINETIC_SWORD_FPA_MODEL = ITEMS.register("kinetic_sword_fpa_model",
            () -> new com.mechaniciancombat.item.KineticSwordFpaItem(new Item.Properties().stacksTo(1)));
    /** 战旗施法第一人称的隐藏控制器物品。 */
    public static final RegistryObject<Item> WAR_BANNER_FPA_MODEL = ITEMS.register("war_banner_fpa_model",
            () -> new com.mechaniciancombat.item.WarBannerFpaItem(new Item.Properties().stacksTo(1)));
    /** 猎矛部署第一人称的隐藏控制器物品。 */
    public static final RegistryObject<Item> HUNTER_ANCHOR_FPA_MODEL = ITEMS.register("hunter_anchor_fpa_model",
            () -> new com.mechaniciancombat.item.CombatPropFpaItem(new Item.Properties().stacksTo(1)));
    /** 背包炮台第一人称的隐藏控制器物品。 */
    public static final RegistryObject<Item> PORTABLE_TURRET_FPA_MODEL = ITEMS.register("portable_turret_fpa_model",
            () -> new com.mechaniciancombat.item.CombatPropFpaItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> AUTO_TURRET_FPA_MODEL = ITEMS.register("auto_turret_fpa_model",
            () -> new com.mechaniciancombat.item.CombatPropFpaItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SOUL_ANCHOR_PROJECTILE_MODEL = ITEMS.register("soul_anchor_projectile_model",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BOLT = ITEMS.register("bolt",
            () -> new MoltenTridentItem(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> WIND_CHARGE = ITEMS.register("wind_charge",
            () -> new WindChargeItem(new Item.Properties()));
    public static final RegistryObject<Item> TAB_ICON = ITEMS.register("tab_icon",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> COMBAT_BACKPACK = backpack("combat_backpack", "combat_backpack");
    public static final List<RegistryObject<Item>> COMBAT_BACKPACKS = List.of(
            COMBAT_BACKPACK,
            backpack("combat_backpack_black", "combat_backpack_black"),
            backpack("combat_backpack_blue", "combat_backpack_blue"),
            backpack("combat_backpack_brown", "combat_backpack_brown"),
            backpack("combat_backpack_cyan", "combat_backpack_cyan"),
            backpack("combat_backpack_gray", "combat_backpack_gray"),
            backpack("combat_backpack_green", "combat_backpack_green"),
            backpack("combat_backpack_light_blue", "combat_backpack_light_blue"),
            backpack("combat_backpack_light_gray", "combat_backpack_light_gray"),
            backpack("combat_backpack_lime", "combat_backpack_lime"),
            backpack("combat_backpack_magenta", "combat_backpack_magenta"),
            backpack("combat_backpack_orange", "combat_backpack_orange"),
            backpack("combat_backpack_pink", "combat_backpack_pink"),
            backpack("combat_backpack_purple", "combat_backpack_purple"),
            backpack("combat_backpack_red", "combat_backpack_red"),
            backpack("combat_backpack_shulker_box", "combat_backpack_shulker_box"),
            backpack("combat_backpack_white", "combat_backpack_white"),
            backpack("combat_backpack_yellow", "combat_backpack_yellow"));

    public static final List<RegistryObject<Item>> BULLETS = List.of(
            bullet("cutting_bolt"), bullet("sculk_bolt"), bullet("explosive_bolt"),
            bullet("rose_quartz_bolt"), bullet("armor_piercing_bolt"));

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.tkgun"))
                    .icon(() -> new ItemStack(TAB_ICON.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(BACKPACK_TABLE_ITEM.get());
                        output.accept(ARMOR_TABLE_ITEM.get());
                        output.accept(BOWGUN.get());
                        output.accept(ABYSSAL_ANCIENT_CANNON.get());
                        output.accept(VERITAS_ET_JUSTITIA.get());
                        output.accept(KINETIC_SWORD.get());
                        output.accept(MECHANICIAN_UNDERSUIT.get());
                        output.accept(MECHANICIAN_CHESTPLATE.get());
                        output.accept(MECHANICIAN_LEGGINGS.get());
                        output.accept(MECHANICIAN_BOOTS.get());
                        output.accept(BOLT.get());
                        output.accept(WIND_CHARGE.get());
                        output.accept(COMBAT_BACKPACK.get());
                        output.accept(BACKPACK_CORE.get());
                        output.accept(PORTABLE_LAUNCHER.get());
                        output.accept(AUTO_PORTABLE_LAUNCHER.get());
                        output.accept(SUMMON_ANTENNA.get());
                        output.accept(WAR_BANNER_ITEM.get());
                        output.accept(JUMP_BACKPACK_THRUSTER.get());
                        output.accept(MISSILE_BOX.get());
                        output.accept(GOLEM_DISPATCH_CAPSULE.get());
                        output.accept(HUNTER_SPEAR.get());
                        output.accept(UNDYING_TOTEM_PART.get());
                        output.accept(PAULDRON_LEFT.get());
                        output.accept(SOUL_CHAIN.get());
                        output.accept(SOUL_ANCHOR_PART.get());
                        output.accept(BACKPACK_COOLDOWN_RESET.get());
                        output.accept(MOLTEN_GOLEM_SPAWN_EGG.get());
                        output.accept(ENTITY_REMOVAL_TOOL.get());
                        BULLETS.forEach(item -> output.accept(item.get()));
                    }).build());

    public static final RegistryObject<net.minecraft.world.entity.EntityType<BoltProjectileEntity>> BOLT_ENTITY =
            ENTITIES.register("bolt_projectile", () -> EntityType.Builder.<BoltProjectileEntity>of(
                    BoltProjectileEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .build(TkGun.id("bolt_projectile").toString()));
    public static final RegistryObject<net.minecraft.world.entity.EntityType<BulletEntity>> BULLET_ENTITY =
            ENTITIES.register("bullet", () -> EntityType.Builder.<BulletEntity>of(
                    BulletEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .updateInterval(1)
                    .build(TkGun.id("bullet").toString()));
    public static final RegistryObject<net.minecraft.world.entity.EntityType<MoltenTridentEntity>> MOLTEN_TRIDENT_ENTITY =
            ENTITIES.register("molten_trident", () -> EntityType.Builder.<MoltenTridentEntity>of(
                    MoltenTridentEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .build(TkGun.id("molten_trident").toString()));
    public static final RegistryObject<net.minecraft.world.entity.EntityType<WindChargeEntity>> WIND_CHARGE_ENTITY =
            ENTITIES.register("wind_charge", () -> EntityType.Builder.<WindChargeEntity>of(
                    WindChargeEntity::new, MobCategory.MISC).sized(0.3125F, 0.3125F)
                    .clientTrackingRange(4).updateInterval(10)
                    .build(TkGun.id("wind_charge").toString()));
    /** 战旗：不可移动的临时增益实体（r774）。 */
    public static final RegistryObject<net.minecraft.world.entity.EntityType<com.mechaniciancombat.entity.WarBannerEntity>> WAR_BANNER_ENTITY =
            ENTITIES.register("war_banner", () -> EntityType.Builder.<com.mechaniciancombat.entity.WarBannerEntity>of(
                    com.mechaniciancombat.entity.WarBannerEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.5F).fireImmune()
                    .clientTrackingRange(10).updateInterval(2)
                    .build(TkGun.id("war_banner").toString()));
    /** 猎矛部署的固定炮台（r774）。 */
    public static final RegistryObject<net.minecraft.world.entity.EntityType<com.mechaniciancombat.entity.HunterTurretEntity>> HUNTER_TURRET_ENTITY =
            ENTITIES.register("hunter_turret", () -> EntityType.Builder
                    .<com.mechaniciancombat.entity.HunterTurretEntity>of(
                            com.mechaniciancombat.entity.HunterTurretEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.6F).fireImmune()
                    .clientTrackingRange(10).updateInterval(2)
                    .build(TkGun.id("hunter_turret").toString()));
    /** 猎矛忠诚回归投射物（r774）。 */
    public static final RegistryObject<net.minecraft.world.entity.EntityType<com.mechaniciancombat.entity.HunterSpearReturnEntity>> HUNTER_SPEAR_RETURN_ENTITY =
            ENTITIES.register("hunter_spear_return", () -> EntityType.Builder
                    .<com.mechaniciancombat.entity.HunterSpearReturnEntity>of(
                            com.mechaniciancombat.entity.HunterSpearReturnEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10).updateInterval(10)
                    .build(TkGun.id("hunter_spear_return").toString()));
    /** 灵魂锚投射物（r955/r984/r993）。 */
    public static final RegistryObject<net.minecraft.world.entity.EntityType<com.mechaniciancombat.entity.SoulAnchorEntity>> SOUL_ANCHOR_ENTITY =
            ENTITIES.register("soul_anchor_projectile", () -> EntityType.Builder
                    .<com.mechaniciancombat.entity.SoulAnchorEntity>of(
                            com.mechaniciancombat.entity.SoulAnchorEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(12).updateInterval(1)
                    .build(TkGun.id("soul_anchor_projectile").toString()));

    public static final RegistryObject<SimpleParticleType> HEAT_VENT = particle("heat_vent");
    public static final RegistryObject<SimpleParticleType> ENERGY_CONVERGE = particle("energy_converge");
    public static final RegistryObject<SimpleParticleType> ENERGY_XPLO = particle("energy_xplo");
    public static final RegistryObject<SimpleParticleType> SHELL_BOOST = particle("shell_boost");
    public static final RegistryObject<SimpleParticleType> MUZZLE_GLOW = particle("muzzle_glow");
    public static final RegistryObject<SimpleParticleType> CUTTING_EXPLO = particle("cutting_explo");
    public static final RegistryObject<SimpleParticleType> SONIC_BOOM = particle("sonic_boom");
    public static final RegistryObject<SimpleParticleType> THUNDER = particle("thunder");
    public static final RegistryObject<SimpleParticleType> GRAPESHOT_IMPACT = particle("grapeshot_impact");
    public static final RegistryObject<SimpleParticleType> ROSE_QUARTZ_IMPACT = particle("rose_quartz_explo");
    public static final RegistryObject<SimpleParticleType> ROSE_QUARTZ_TRAIL = particle("rose_quartz_trail");
    /** 战旗光环火花（8 帧序列，r774）。 */
    public static final RegistryObject<SimpleParticleType> WAR_BANNER_SPARK = particle("war_banner_spark");
    /**
     * 枢德之裁近战技能火花（5 帧，作者原素材 18 FPS）。
     *
     * <p>`veritas_et_justitia_rig.animation.json` 在 `ATK_3_hold`(0.5s) 与
     * `ATK_hold_over`(1.0s) 上埋了这个粒子关键帧；没有注册时 GeckoLib 只打一行
     * "no keyframe handler"，玩家的重劈/蓄力就没有火花 —— 近战打击感缺一块。
     */
    public static final RegistryObject<SimpleParticleType> SKILL_SPARK = particle("skill_spark");

    public static final RegistryObject<SimpleParticleType> GOLEM_ENERGY_CONVERGE = particle("golem_energy_converge");

    public static final RegistryObject<SimpleParticleType> GOLEM_ENERGY_CONVERGE_2 = particle("golem_energy_converge_2");

    public static final RegistryObject<SimpleParticleType> EXPLOSIVE_BOLT_IMPACT = particle("explosive_bolt_impact");

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        CREATIVE_TABS.register(modBus);
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(ModRegistries::entityAttributes);
    }

    private static RegistryObject<Item> backpack(String id, String texture) {
        return ITEMS.register(id, () -> new CombatBackpackItem(new Item.Properties().stacksTo(1), texture));
    }

    private static RegistryObject<Item> bullet(String id) {
        return ITEMS.register(id, () -> new BoltItem(new Item.Properties()));
    }

    private static RegistryObject<SimpleParticleType> particle(String id) {
        return PARTICLES.register(id, () -> new SimpleParticleType(true));
    }

    private static RegistryObject<net.minecraft.sounds.SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(TkGun.id(id)));
    }
    private static void entityAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(MOLTEN_GOLEM.get(), net.minecraft.world.entity.animal.IronGolem.createAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 400.0D).build());
    }
    private ModRegistries() { }
}
