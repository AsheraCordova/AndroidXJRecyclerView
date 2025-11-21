//start - license
/*
 * Copyright (c) 2025 Ashera Cordova
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
 */
//end - license
package com.xwray.groupie;

import r.android.annotation.NonNull;

import java.util.Collection;

class GroupUtils {
    @NonNull
    static Item getItem(Collection<? extends RVGroup> groups, int position) {
        int previousPosition = 0;

        for ( RVGroup group : groups) {
            int size = group.getItemCount();
            if (size + previousPosition > position) {
                return group.getItem(position - previousPosition);
            }
            previousPosition += size;
        }

        throw new IndexOutOfBoundsException("Wanted item at " + position + " but there are only "
                + previousPosition + " items");
    }

    static int getItemCount(@NonNull Collection<? extends RVGroup> groups) {
        int size = 0;
        for ( RVGroup group : groups) {
            size += group.getItemCount();
        }
        return size;
    }
}
