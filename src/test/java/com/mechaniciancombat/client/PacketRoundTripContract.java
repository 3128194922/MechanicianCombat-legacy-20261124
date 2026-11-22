package com.mechaniciancombat.client;

import com.mechaniciancombat.net.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 网络包编解码的**往返一致性**：写入 → 读取 → 与原值逐字段比对。
 *
 * <p>这比静态数 write/read 调用次数可靠：`readableBytes() > 0` 的宽读是刻意的向后兼容设计，
 * 数调用次数会把它误判成不对称；而真正漏读/漏写一个字段，往返比对一定会失败。
 *
 * <p>同时断言**缓冲区被完全消费**（没有残留字节），否则说明写入多于读取，
 * 后续包会读到错位的数据。
 */
public final class PacketRoundTripContract {
	public static void main(String[] args) {
		List<String> failures = new ArrayList<>();
		roundTrip("WarBannerSkillPayload",
				() -> new WarBannerSkillPayload(11, 1234L, 23, true),
				WarBannerSkillPayload.CODEC, failures);
		roundTrip("WarBannerAnchorPayload",
				() -> new WarBannerAnchorPayload(1.5D, 64.25D, -3.75D, 90.5F),
				WarBannerAnchorPayload.CODEC, failures);
		roundTrip("JumpBackpackPayload",
				() -> new JumpBackpackPayload(true), JumpBackpackPayload.CODEC, failures);
		roundTrip("MissileBoxPayload",
				() -> new MissileBoxPayload(false, 8.5D, 70.0D, -9.25D,
						1.25D, -0.5D, 0.55D, 0.45D, missilePorts()),
				MissileBoxPayload.CODEC, failures);
		roundTrip("HunterSpearSkillPayload",
				() -> new HunterSpearSkillPayload(12, 5678L, 23),
				HunterSpearSkillPayload.CODEC, failures);
		roundTrip("HunterSpearAnchorPayload",
				() -> new HunterSpearAnchorPayload(2.5D, 63.5D, 4.5D, 45.0F),
				HunterSpearAnchorPayload.CODEC, failures);
		roundTrip("SoulAnchorInputPayload",
				() -> new SoulAnchorInputPayload(true, false, true),
				SoulAnchorInputPayload.CODEC, failures);
		roundTrip("SoulAnchorStatusPayload",
				() -> new SoulAnchorStatusPayload(3, 21, 2, 300, 0xFFFF00FF),
				SoulAnchorStatusPayload.CODEC, failures);
		roundTrip("KineticSwordMovementPayload",
				() -> new KineticSwordMovementPayload(true),
				KineticSwordMovementPayload.CODEC, failures);
		roundTrip("KineticStancePayload",
				KineticStancePayload::new, KineticStancePayload.CODEC, failures);
		roundTrip("GunStatePayload",
				() -> new GunStatePayload(9, (byte) 0, 12, 40, 2, 3,
						true, false, 77, true, 30, 0, 10, "tkgun:bolt", 5,
						false, true, false, true, false, false, false,
						true, false, true, false, true, 62, true),
				GunStatePayload.CODEC, failures);
		roundTrip("ArmorAbilityPayload",
				() -> new ArmorAbilityPayload(true, false, true, 3, 7, 900, 40, 120),
				ArmorAbilityPayload.CODEC, failures);
		roundTrip("SoulChainPayload",
				() -> new SoulChainPayload(java.util.List.of(
						new SoulChainPayload.ChainNode(5, 1.5D, 64.5D, 2.5D),
						new SoulChainPayload.ChainNode(6, 3.5D, 64.5D, 4.5D)), 30),
				SoulChainPayload.CODEC, failures);
		roundTrip("HelmetTogglePayload",
				() -> new HelmetTogglePayload(), HelmetTogglePayload.CODEC, failures);
		roundTrip("HelmetStatePayload",
				() -> new HelmetStatePayload(java.util.UUID.randomUUID(), true, 4321L, 15),
				HelmetStatePayload.CODEC, failures);
		roundTrip("RecipeEditorActionPayload",
				() -> new RecipeEditorActionPayload(
						RecipeEditorActionPayload.ACTION_SAVE, 1, true,
						"tkgun:test_recipe", 0.35F, 200),
				RecipeEditorActionPayload.CODEC, failures);
		roundTrip("GunFirePayload",
				() -> new GunFirePayload(1.0D, 2.0D, 3.0D, 0.0D, 0.0D, 1.0D),
				GunFirePayload.CODEC, failures);
		roundTrip("GunRollPayload",
				() -> new GunRollPayload(1, -1, 90.0F), GunRollPayload.CODEC, failures);
		roundTrip("GunMeleePayload",
				() -> new GunMeleePayload(true, false), GunMeleePayload.CODEC, failures);

		if (!failures.isEmpty()) {
			throw new AssertionError("packet round-trip failures: " + String.join("; ", failures));
		}
		checkEveryRegisteredPacketHasReceiver();
		System.out.println("PacketRoundTripContract: every payload survives encode/decode with no trailing bytes");
	}

	/**
	 * **每个注册过的包都必须有接收端**。
	 *
	 * <p>Forge 的 `SimpleChannel` 对"注册了但没人处理"的包不会报错 ——
	 * 它会照常解码，然后静默丢弃。表现就是"某个功能偶尔没反应"，
	 * 且日志里一点痕迹都没有。这条契约把三类名单对齐：
	 * `ModNetwork.register(...)` 注册表 ↔ 服务端 `registerGlobalReceiver` ↔ 客户端同名调用。
	 */
	private static void checkEveryRegisteredPacketHasReceiver() {
		java.nio.file.Path network = java.nio.file.Path.of(
				"src/main/java/com/mechaniciancombat/net/ModNetwork.java");
		java.nio.file.Path server = java.nio.file.Path.of(
				"src/main/java/com/mechaniciancombat/TkGunServer.java");
		java.nio.file.Path client = java.nio.file.Path.of(
				"src/main/java/com/mechaniciancombat/client/TkGunClient.java");
		for (java.nio.file.Path path : new java.nio.file.Path[]{network, server, client}) {
			if (!java.nio.file.Files.isRegularFile(path)) {
				return;                       // 打包产物里没有源码：跳过
			}
		}
		String networkText = readText(network);
		String serverText = readText(server);
		String clientText = readText(client);

		// 注册表：`register(<id>, <Type>.ID, ...)`
		java.util.Set<String> registered = new java.util.TreeSet<>();
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("register\\(\\s*\\d+\\s*,\\s*(\\w+)\\.ID").matcher(networkText);
		while (m.find()) {
			registered.add(m.group(1));
		}
		if (registered.isEmpty()) {
			throw new AssertionError("could not parse any register(...) calls in " + network);
		}
		// 接收端：允许全限定名或简单名两种写法（本地两种都有）。
		java.util.Set<String> handled = new java.util.TreeSet<>();
		for (String text : new String[]{serverText, clientText}) {
			java.util.regex.Matcher h = java.util.regex.Pattern
					.compile("registerGlobalReceiver\\(\\s*(?:com\\.mechaniciancombat\\.net\\.)?(\\w+)\\.ID")
					.matcher(text);
			while (h.find()) {
				handled.add(h.group(1));
			}
		}
		java.util.List<String> orphaned = new java.util.ArrayList<>();
		for (String type : registered) {
			if (!handled.contains(type)) {
				orphaned.add(type);
			}
		}
		if (!orphaned.isEmpty()) {
			throw new AssertionError("registered packets with no receiver "
					+ "(Forge decodes then silently drops them): " + String.join(", ", orphaned));
		}
	}

	private static String readText(java.nio.file.Path path) {
		try {
			return java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError("cannot read " + path, error);
		}
	}

	private static <T> void roundTrip(String name, Supplier<T> factory, PacketCodec<T> codec,
									  List<String> failures) {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			T original = factory.get();
			codec.encoder().accept(original, buffer);
			T decoded = codec.decoder().apply(buffer);
			// record 的 equals 对数组字段是引用比较，导弹舱的 ports 需要按内容比。
			boolean equal = original.equals(decoded)
					|| (original instanceof MissileBoxPayload a && decoded instanceof MissileBoxPayload b
							&& a.release() == b.release()
							&& a.cameraX() == b.cameraX() && a.cameraY() == b.cameraY()
							&& a.cameraZ() == b.cameraZ() && a.cameraYaw() == b.cameraYaw()
							&& a.cameraPitch() == b.cameraPitch()
							&& a.halfHorizontalTan() == b.halfHorizontalTan()
							&& a.halfVerticalTan() == b.halfVerticalTan()
							&& java.util.Arrays.equals(a.ports(), b.ports()));
			if (!equal) {
				failures.add(name + " value mismatch: " + original + " != " + decoded);
			}
			if (buffer.readableBytes() != 0) {
				failures.add(name + " left " + buffer.readableBytes()
						+ " unread bytes (encoder writes more than decoder reads)");
			}
		} catch (Throwable error) {
			failures.add(name + " threw " + error.getClass().getSimpleName() + ": " + error.getMessage());
		} finally {
			buffer.release();
		}
	}

	private PacketRoundTripContract() {
	}

	/** 六个发射口 × 3 个分量，用非零值确保每个分量都被真正编码。 */
	private static double[] missilePorts() {
		double[] ports = new double[18];
		for (int i = 0; i < ports.length; i++) {
			ports[i] = (i + 1) * 0.125D;
		}
		return ports;
	}
}
