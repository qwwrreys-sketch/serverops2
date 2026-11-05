package com.serverops.common;

/** A role. Its position in {@link OpsData#roles} is its rank (index 0 = most senior). */
public class RoleDef {
    public String id;
    public String name;
    /** 0xRRGGBB */
    public int color;
    /** bit mask of {@link Perm} */
    public long perms;

    public RoleDef() {}

    public RoleDef(String id, String name, int color, long perms) {
        this.id = id;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.perms = perms;
    }

    public boolean has(Perm p) {
        return Perm.has(perms, p);
    }
}
