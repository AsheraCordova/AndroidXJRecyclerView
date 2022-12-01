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
