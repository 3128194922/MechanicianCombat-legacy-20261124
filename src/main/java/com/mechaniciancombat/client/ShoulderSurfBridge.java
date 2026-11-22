package com.mechaniciancombat.client;

/** Optional camera handoff without a compile-time or runtime mod dependency. */
public final class ShoulderSurfBridge {
	private static Object previous;
	private static final String API = "com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing";
	private static final String INTERFACE = "com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing";
	private static final String PERSPECTIVE = "com.github.exopandora.shouldersurfing.api.model.Perspective";

	private static Object instance() throws ReflectiveOperationException {
		return Class.forName(API).getMethod("getInstance").invoke(null);
	}

	private static Object current() throws ReflectiveOperationException {
		return Class.forName(PERSPECTIVE).getMethod("current").invoke(null);
	}

	private static void change(Object api, Object perspective) throws ReflectiveOperationException {
		Class.forName(INTERFACE).getMethod("changePerspective", Class.forName(PERSPECTIVE))
				.invoke(api, perspective);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object backPerspective() throws ReflectiveOperationException {
		return Enum.valueOf((Class<? extends Enum>) Class.forName(PERSPECTIVE).asSubclass(Enum.class), "THIRD_PERSON_BACK");
	}

	public static void enterBuiltInCamera() throws ReflectiveOperationException {
		Object api = instance();
		if (api == null) return;
		previous = current();
		change(api, backPerspective());
	}

	public static void ensureBuiltInCamera() throws ReflectiveOperationException {
		Object api = instance();
		if (api != null && isOverShoulderActive()) change(api, backPerspective());
	}

	public static void restorePreviousCamera() throws ReflectiveOperationException {
		Object saved = previous;
		previous = null;
		Object api = instance();
		if (api != null && saved != null) change(api, saved);
	}

	public static boolean isOverShoulderActive() {
		try { return instance() != null && "SHOULDER_SURFING".equals(((Enum<?>) current()).name()); }
		catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return false; }
	}

	public static String describe() {
		try { return instance() == null ? "无" : String.valueOf(current()); }
		catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return "不可用"; }
	}

	private ShoulderSurfBridge() { }
}
