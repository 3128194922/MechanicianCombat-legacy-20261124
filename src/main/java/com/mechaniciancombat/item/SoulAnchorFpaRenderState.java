package com.mechaniciancombat.item;

/** 第一人称灵魂锚模型当前渲染模式。 */
public final class SoulAnchorFpaRenderState {
	public enum Mode {
		NONE,
		READY,
		THROW
	}

	private static volatile Mode mode = Mode.NONE;

	public static Mode mode() {
		return mode;
	}

	public static void set(Mode value) {
		mode = value == null ? Mode.NONE : value;
	}

	private SoulAnchorFpaRenderState() {
	}
}
