package org.panda_lang.reposilite.repository;

public interface IQuota {
    long getUsage();
    long getCapacity();

    default boolean notFull() {
        return getUsage() < getCapacity();
    }
}
