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