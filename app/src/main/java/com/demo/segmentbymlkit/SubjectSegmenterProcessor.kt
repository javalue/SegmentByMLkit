/*
 * Copyright 2023 Google LLC. All rights reserved.
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

package com.demo.segmentbymlkit

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions

/** A processor to run Subject Segmenter. */
class SubjectSegmenterProcessor {
    private val subjectSegmenter: SubjectSegmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableMultipleSubjects(
            SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask()
                .build()
        ).build()
    )
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    fun detectInImage(image: InputImage): Task<SubjectSegmentationResult?> {
        this.imageWidth = image.width
        this.imageHeight = image.height
        return subjectSegmenter.process(image)
    }

    companion object {
        private const val TAG = "SbjSegmenterProcessor"
    }
}
