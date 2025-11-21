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

import r.android.os.AsyncTask;
import r.android.annotation.NonNull;
import r.android.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import java.lang.ref.WeakReference;
import java.util.Collection;

/**
 * An async task implementation that runs {@link DiffUtil#calculateDiff(DiffUtil.Callback)}
 * in a background thread. This task will call {@link AsyncDiffUtil.Callback#onDispatchAsyncResult(Collection)}
 * passing the new list just before dispatching the diff result to the provided
 * {@link DiffUtil.Callback} so that the new list.
 * <p>This task is executed via {@link AsyncDiffUtil#calculateDiff(Collection, DiffUtil.Callback, OnAsyncUpdateListener, boolean)}.
 */
class DiffTask extends AsyncTask<Void, Void, DiffUtil.DiffResult> {
    @NonNull private final DiffUtil.Callback diffCallback;
    private final WeakReference<AsyncDiffUtil> asyncListDiffer;
    private final int runGeneration;
    private final boolean detectMoves;
    @Nullable private WeakReference<OnAsyncUpdateListener> onAsyncUpdateListener;
    private Exception backgroundException = null;

    DiffTask(@NonNull AsyncDiffUtil asyncDiffUtil,
             @NonNull DiffUtil.Callback callback,
             int runGeneration,
             boolean detectMoves,
             @Nullable OnAsyncUpdateListener onAsyncUpdateListener) {
        this.diffCallback = callback;
        this.asyncListDiffer = new WeakReference<>(asyncDiffUtil);
        this.runGeneration = runGeneration;
        this.detectMoves = detectMoves;
        if (onAsyncUpdateListener != null) {
            this.onAsyncUpdateListener = new WeakReference<>(onAsyncUpdateListener);
        }
    }

    @Override
    @Nullable
    protected DiffUtil.DiffResult doInBackground(Void... voids) {
        try {
            return DiffUtil.calculateDiff(diffCallback, detectMoves);
        } catch (Exception e) {
            backgroundException = e;
            return null;
        }
    }

    @Override
    protected void onPostExecute(@Nullable DiffUtil.DiffResult diffResult) {
        if (backgroundException != null) {
            throw new RuntimeException(backgroundException);
        }
        AsyncDiffUtil async = asyncListDiffer.get();
        if (shouldDispatchResult(diffResult, async)) {
            async.getAsyncDiffUtilCallback().onDispatchAsyncResult(async.getGroups());
            diffResult.dispatchUpdatesTo(async.getAsyncDiffUtilCallback());
            if (onAsyncUpdateListener != null && onAsyncUpdateListener.get() != null) {
                onAsyncUpdateListener.get().onUpdateComplete();
            }
        }
    }

    private boolean shouldDispatchResult(@Nullable DiffUtil.DiffResult diffResult, AsyncDiffUtil async) {
        return diffResult != null && async != null && runGeneration == async.getMaxScheduledGeneration();
    }
}
