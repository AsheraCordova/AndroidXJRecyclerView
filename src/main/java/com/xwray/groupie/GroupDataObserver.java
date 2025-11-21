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
