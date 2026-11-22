package com.mechaniciancombat.item;

/** 隐藏的第一人称道具动画共享的客户端模式。 */
public final class CombatPropFpaRenderState {
	public enum Mode {
		NONE,
		HUNTER_ANCHOR,
		READY,
		AIM,
		CD
	}

	private static volatile Mode mode = Mode.NONE;

	public static Mode mode() {
		return mode;
	}

	public static void set(Mode value) {
		mode = value == null ? Mode.NONE : value;
	}

	private CombatPropFpaRenderState() {
	}
}
