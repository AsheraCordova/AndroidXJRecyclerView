package com.xwray.groupie;

import r.android.annotation.NonNull;

public interface GroupDataObserver {
    void onChanged(@NonNull  RVGroup group);

    void onItemInserted(@NonNull  RVGroup group, int position);

    void onItemChanged(@NonNull  RVGroup group, int position);

    void onItemChanged(@NonNull  RVGroup group, int position, Object payload);

    void onItemRemoved(@NonNull  RVGroup group, int position);

    void onItemRangeChanged(@NonNull  RVGroup group, int positionStart, int itemCount);

    void onItemRangeChanged(@NonNull  RVGroup group, int positionStart, int itemCount, Object payload);

    void onItemRangeInserted(@NonNull  RVGroup group, int positionStart, int itemCount);

    void onItemRangeRemoved(@NonNull  RVGroup group, int positionStart, int itemCount);

    void onItemMoved(@NonNull  RVGroup group, int fromPosition, int toPosition);

    void onDataSetInvalidated();
}
