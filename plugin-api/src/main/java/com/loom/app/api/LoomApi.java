package com.loom.app.api;

/** 宿主公开的契约常量，插件可用它做构建期与装载期的一致性检查。 */
public final class LoomApi {

    /** 插件契约版本。 */
    public static final int CONTRACT_VERSION = 1;

    /**
     * Compose ABI 标识：宿主当前使用的 Compose BOM。
     * 声明 composeScreen 的插件必须把同一个值写进 plugin.json 的 "composeAbi"。
     */
    public static final String COMPOSE_ABI = "2024.09.03";

    private LoomApi() {
    }
}
