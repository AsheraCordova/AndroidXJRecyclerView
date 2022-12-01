package com.xwray.groupie;

import r.android.annotation.NonNull;

/**
 * A group of items, to be used in an adapter.
 */
public interface RVGroup {

    int getItemCount();

    @NonNull Item getItem(int position);

    /**
     * Gets the position of an item inside this Group
     * @param item item to return position of
     * @return The position of the item or -1 if not present
     */
    int getPosition(@NonNull Item item);

    void registerGroupDataObserver(@NonNull GroupDataObserver groupDataObserver);

    void unregisterGroupDataObserver(@NonNull GroupDataObserver groupDataObserver);

}