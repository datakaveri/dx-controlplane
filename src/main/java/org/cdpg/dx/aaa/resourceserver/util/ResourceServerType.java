package org.cdpg.dx.aaa.resourceserver.util;

public enum ResourceServerType {
    NGSILD("NGSILD"),
    OGC("OGC"),FILE("FILE"),GATEWAY("GATEWAY");

    ResourceServerType(String resourceServerType) {
        this.resourceServerType = resourceServerType;
    }
    final String resourceServerType;
}
