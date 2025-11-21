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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import r.android.annotation.NonNull;

/**
 * An ExpandableGroup is one "base" content item with a list of children (any of which
 * may themselves be a group.)
 **/

public class ExpandableGroup extends NestedGroup {

    private boolean isExpanded = false;
    private final RVGroup  parent;
    private final List<RVGroup> children = new ArrayList<>();

    public <T extends RVGroup & ExpandableItem> ExpandableGroup(T expandableItem) {
        this.parent = expandableItem;
        expandableItem.setExpandableGroup(this);
    }

    public <T extends RVGroup & ExpandableItem> ExpandableGroup(T expandableItem, boolean isExpanded) {
        this.parent = expandableItem;
        expandableItem.setExpandableGroup(this);
        this.isExpanded = isExpanded;
    }

    @Override
    public void add(int position, @NonNull  RVGroup group) {
        super.add(position, group);
        children.add(position, group);
        if (isExpanded) {
            final int notifyPosition = 1 + GroupUtils.getItemCount(children.subList(0, position));
            notifyItemRangeInserted(notifyPosition, group.getItemCount());
        }
    }

    @Override
    public void add(@NonNull  RVGroup group) {
        super.add(group);
        if (isExpanded) {
            int itemCount = getItemCount();
            children.add(group);
            notifyItemRangeInserted(itemCount, group.getItemCount());
        } else {
            children.add(group);
        }
    }

    @Override
    public void addAll(@NonNull Collection<? extends RVGroup> groups) {
        if (groups.isEmpty()) {
            return;
        }
        super.addAll(groups);
        if (isExpanded) {
            int itemCount = getItemCount();
            this.children.addAll(groups);
            notifyItemRangeInserted(itemCount, GroupUtils.getItemCount(groups));
        } else {
            this.children.addAll(groups);
        }
    }

    @Override
    public void addAll(int position, @NonNull Collection<? extends RVGroup> groups) {
        if (groups.isEmpty()) {
            return;
        }
        super.addAll(position, groups);
        this.children.addAll(position, groups);

        if (isExpanded) {
            final int notifyPosition = 1 + GroupUtils.getItemCount(children.subList(0, position));
            notifyItemRangeInserted(notifyPosition, GroupUtils.getItemCount(groups));
        }
    }


    @Override
    public void remove(@NonNull  RVGroup group) {
        if (!this.children.contains(group)) return;
        super.remove(group);
        if (isExpanded) {
            int position = getItemCountBeforeGroup(group);
            children.remove(group);
            notifyItemRangeRemoved(position, group.getItemCount());
        } else {
            children.remove(group);
        }
    }

    @Override
    public void replaceAll(@NonNull Collection<? extends RVGroup> groups) {
        if (isExpanded) {
            super.replaceAll(groups);
            children.clear();
            children.addAll(groups);
            notifyDataSetInvalidated();
        } else {
            children.clear();
            children.addAll(groups);
        }
    }

    @Override
    public void removeAll(@NonNull Collection<? extends RVGroup> groups) {
        if (groups.isEmpty() || !this.children.containsAll(groups)) return;
        super.removeAll(groups);
        if (isExpanded) {
            this.children.removeAll(groups);
            for ( RVGroup group : groups) {
                int position = getItemCountBeforeGroup(group);
                children.remove(group);
                notifyItemRangeRemoved(position, group.getItemCount());
            }
        } else {
            this.children.removeAll(groups);
        }
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    @NonNull
     public RVGroup getGroup(int position) {
        if (position == 0) {
            return parent;
        } else {
            return children.get(position - 1);
        }
    }

    @Override
    public int getPosition(@NonNull  RVGroup group) {
        if (group == parent) {
            return 0;
        }
        int index = children.indexOf(group);
        if (index >= 0) {
            return index + 1;
        }
        return -1;
    }

    public int getGroupCount() {
        return 1 + (isExpanded ? children.size() : 0);
    }

    public int getChildCount() {
        return children.size();
    }

    public void onToggleExpanded() {
        int oldSize = getItemCount();
        isExpanded = !isExpanded;
        int newSize = getItemCount();
        if (oldSize > newSize) {
            notifyItemRangeRemoved(newSize, oldSize - newSize);
        } else {
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        }
    }

    public void setExpanded(boolean isExpanded) {
        if (this.isExpanded != isExpanded) {
            onToggleExpanded();
        }
    }

    private boolean dispatchChildChanges( RVGroup group) {
        return isExpanded || group == parent;
    }

    @Override
    public void onChanged(@NonNull  RVGroup group) {
        if (dispatchChildChanges(group)) {
            super.onChanged(group);
        }
    }

    @Override
    public void onItemInserted(@NonNull  RVGroup group, int position) {
        if (dispatchChildChanges(group)) {
            super.onItemInserted(group, position);
        }
    }

    @Override
    public void onItemChanged(@NonNull  RVGroup group, int position) {
        if (dispatchChildChanges(group)) {
            super.onItemChanged(group, position);
        }
    }

    @Override
    public void onItemChanged(@NonNull  RVGroup group, int position, Object payload) {
        if (dispatchChildChanges(group)) {
            super.onItemChanged(group, position, payload);
        }
    }

    @Override
    public void onItemRemoved(@NonNull  RVGroup group, int position) {
        if (dispatchChildChanges(group)) {
            super.onItemRemoved(group, position);
        }
    }

    @Override
    public void onItemRangeChanged(@NonNull  RVGroup group, int positionStart, int itemCount) {
        if (dispatchChildChanges(group)) {
            super.onItemRangeChanged(group, positionStart, itemCount);
        }
    }

    @Override
    public void onItemRangeChanged(@NonNull  RVGroup group, int positionStart, int itemCount, Object payload) {
        if (dispatchChildChanges(group)) {
            super.onItemRangeChanged(group, positionStart, itemCount, payload);
        }
    }

    @Override
    public void onItemRangeInserted(@NonNull  RVGroup group, int positionStart, int itemCount) {
        if (dispatchChildChanges(group)) {
            super.onItemRangeInserted(group, positionStart, itemCount);
        }
    }

    @Override
    public void onItemRangeRemoved(@NonNull  RVGroup group, int positionStart, int itemCount) {
        if (dispatchChildChanges(group)) {
            super.onItemRangeRemoved(group, positionStart, itemCount);
        }
    }

    @Override
    public void onItemMoved(@NonNull  RVGroup group, int fromPosition, int toPosition) {
        if (dispatchChildChanges(group)) {
            super.onItemMoved(group, fromPosition, toPosition);
        }
    }

    @Override
    public void onDataSetInvalidated() {
        if (isExpanded) {
            super.onDataSetInvalidated();
        }
    }
}
