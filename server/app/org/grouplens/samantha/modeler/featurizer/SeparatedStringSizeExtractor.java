/*
 * Copyright (c) [2016-2017] [University of Minnesota]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.grouplens.samantha.modeler.featurizer;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.grouplens.samantha.modeler.model.IndexSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeparatedStringSizeExtractor implements FeatureExtractor {
    private static final long serialVersionUID = 1L;
    private static Logger logger = LoggerFactory.getLogger(SeparatedStringSizeExtractor.class);
    private final String indexName;
    private final String attrName;
    private final String feaName;
    private final String separator;
    private final Integer maxFeatures;
    private final boolean alwaysExtract;

    /***
     * Extract the number of sub strings after splitting by the separator as value.
     * @param indexName The name of the key index to use.
     * @param attrName The name of the attribute to get the string to split. This will also be used as the feature
     *                 key to convert to index.
     * @param feaName The name of the feature after extracting.
     * @param separator This actual string to count as the separator. Note that this is not a regex pattern.
     * @param maxFeatures The maximum number of sub strings. It can be null. When not null, it serves as a maximum
     *                    of the extracted feature value.
     */
    public SeparatedStringSizeExtractor(String indexName,
                                        String attrName,
                                        String feaName,
                                        String separator,
                                        Integer maxFeatures,
                                        boolean alwaysExtract) {
        this.indexName = indexName;
        this.attrName = attrName;
        this.feaName = feaName;
        this.separator = separator;
        this.maxFeatures = maxFeatures;
        this.alwaysExtract = alwaysExtract;
    }

    public Map<String, List<Feature>> extract(JsonNode entity, boolean update,
                                              IndexSpace indexSpace) {
        Map<String, List<Feature>> feaMap = new HashMap<>();
        if (entity.has(attrName) || alwaysExtract) {
            List<Feature> features = new ArrayList<>();
            String attr = "";
            if (entity.has(attrName)) {
                attr = entity.get(attrName).asText();
            }
            int size = StringUtils.countMatches(attr, separator) + 1;
            if ("".equals(attr) && !alwaysExtract) {
                size = 0;
            }
            if (maxFeatures != null && size > maxFeatures) {
                size = maxFeatures;
            }
            String key = FeatureExtractorUtilities.composeKey(attrName, "size");
            FeatureExtractorUtilities.getOrSetIndexSpaceToFeaturize(features, update,
                    indexSpace, indexName, key, size);
            feaMap.put(feaName, features);
        } else {
            logger.warn("{} is not present in {}", attrName, entity);
        }
        return feaMap;
    }
}
