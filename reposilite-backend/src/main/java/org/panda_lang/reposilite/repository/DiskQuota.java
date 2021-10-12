/*
 * Copyright (c) 2020 Dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.panda_lang.reposilite.repository;

import org.panda_lang.reposilite.utils.FilesUtils;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

final class DiskQuota implements IQuota {
    private final DiskQuota parent;
    private final AtomicLong quota;
    private final AtomicLong usage;
    private String toString = null;

    private DiskQuota(DiskQuota parent, long quota, long usage) {
        this.parent = parent;
        this.quota = new AtomicLong(quota);
        this.usage = new AtomicLong(usage);
    }

    void allocate(long size) {
        if (parent != null)
            parent.allocate(size);
        toString = null;
        usage.addAndGet(size);
    }

    public boolean notFull() {
        return (this.parent == null || this.parent.notFull()) && usage.get() < quota.get();
    }

    @Override
    public long getUsage() {
        return usage.get();
    }

    @Override
    public long getCapacity() {
        return quota.get();
    }

    @Override
    public String toString() {
        String ret = toString;
        if (ret == null) {
            ret = toString = FilesUtils.bytesToDisplay(getUsage()) + '/' + FilesUtils.bytesToDisplay(getCapacity());
        }
        return ret;
    }

    static DiskQuota none() {
        return new DiskQuota(null, 0, 0);
    }

    static DiskQuota unlimited() {
        return unlimited(null);
    }

    static DiskQuota unlimited(DiskQuota parent) {
        return new DiskQuota(parent, Long.MAX_VALUE, 0);
    }

    static DiskQuota ofPercentage(File dir, String value) {
        if (value.charAt(value.length() - 1) == '%')
            value = value.substring(0, value.length() - 1);
        long max = dir.getTotalSpace();
        float percent = Float.parseFloat(value) / 100F;
        return new DiskQuota(null, Math.round(max * percent), 0);
    }

    static DiskQuota ofPercentage(DiskQuota parent, String value) {
        if (value.charAt(value.length() - 1) == '%')
            value = value.substring(0, value.length() - 1);
        float percent = Float.parseFloat(value) / 100F;
        return new DiskQuota(parent, Math.round(parent.getCapacity() * percent), 0);
    }

    static DiskQuota of(String capacity) {
        return of(FilesUtils.displayToBytes(capacity));
    }

    static DiskQuota of(long capacity) {
        return of(null, capacity);
    }

    static DiskQuota of(DiskQuota parent, String capacity) {
        return of(parent, FilesUtils.displayToBytes(capacity));
    }
    static DiskQuota of(DiskQuota parent, long capacity) {
        return new DiskQuota(parent, capacity, 0);
    }
}
