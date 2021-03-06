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

package org.grouplens.samantha.server.evaluator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.grouplens.samantha.modeler.instance.GroupedEntityList;
import org.grouplens.samantha.modeler.metric.MetricResult;
import org.grouplens.samantha.server.expander.EntityExpander;
import org.grouplens.samantha.server.expander.ExpanderUtilities;
import org.grouplens.samantha.server.predictor.Prediction;
import org.grouplens.samantha.modeler.metric.Metric;
import org.grouplens.samantha.server.indexer.Indexer;
import org.grouplens.samantha.server.io.RequestContext;
import org.grouplens.samantha.server.predictor.Predictor;
import org.grouplens.samantha.modeler.dao.EntityDAO;
import play.Logger;
import play.api.data.ObjectMapping;
import play.libs.Json;

import java.util.ArrayList;
import java.util.List;

public class PredictionEvaluator implements Evaluator {
    final private Predictor predictor;
    final private EntityDAO entityDAO;
    final private List<EntityExpander> expanders;
    final private List<String> groupKeys;
    final private List<Metric> metrics;
    final private List<Indexer> indexers;
    final private List<Indexer> predIndexers;
    final private String labelAttr;
    final private String separator;

    public PredictionEvaluator(Predictor predictor,
                               EntityDAO entityDAO,
                               List<EntityExpander> expanders,
                               List<String> groupKeys,
                               List<Metric> metrics,
                               List<Indexer> indexers,
                               List<Indexer> predIndexers,
                               String labelAttr,
                               String separator) {
        this.predictor = predictor;
        this.entityDAO = entityDAO;
        this.expanders = expanders;
        this.metrics = metrics;
        this.indexers = indexers;
        this.groupKeys = groupKeys;
        this.predIndexers = predIndexers;
        this.labelAttr = labelAttr;
        this.separator = separator;
    }

    private void getPredictionMetrics(RequestContext requestContext, List<ObjectNode> entityList) {
        List<ObjectNode> labels = new ArrayList<>();
        List<Prediction> preds;
        List<Prediction> predictions;
        if (labelAttr != null && separator != null) {
            List<ObjectNode> topreds = new ArrayList<>();
            for (ObjectNode entity : entityList) {
                String labelStr = entity.get(labelAttr).asText();
                if (!"".equals(labelStr)) {
                    topreds.add(entity);
                }
            }
            preds = new ArrayList<>();
            if (topreds.size() > 0) {
                predictions = predictor.predict(topreds, requestContext);
                for (Prediction pred : predictions) {
                    String[] labelValues = pred.getEntity().get(labelAttr).asText().split(separator, -1);
                    double[] scores = pred.getScores();
                    for (int i = 0; i < labelValues.length; i++) {
                        ObjectNode label = Json.newObject();
                        label.put(labelAttr, labelValues[i]);
                        preds.add(new Prediction(label, null, scores[i], null));
                        labels.add(label);
                    }
                }
            } else {
                predictions = new ArrayList<>();
            }
        } else {
            preds = predictor.predict(entityList, requestContext);
            predictions = preds;
            for (Prediction pred : preds) {
                labels.add(pred.getEntity());
            }
        }
        for (Metric metric : metrics) {
            metric.add(labels, preds);
        }
        for (Indexer indexer : predIndexers) {
            for (Prediction pred : predictions) {
                indexer.index(pred.toJson(), requestContext);
            }
        }
    }

    public Evaluation evaluate(RequestContext requestContext) {
        if (groupKeys != null && groupKeys.size() > 0) {
            Logger.info("Note that the input evaluation data must be sorted by the group keys, e.g. groupId");
        }
        GroupedEntityList groupedEntityList = new GroupedEntityList(groupKeys, 1, entityDAO);
        List<ObjectNode> entityList;
        int cnt = 0;
        int skipped = 0;
        while ((entityList = groupedEntityList.getNextGroup()).size() > 0) {
            entityList = ExpanderUtilities.expand(entityList, expanders, requestContext);
            if (entityList.size() > 0) {
                getPredictionMetrics(requestContext, entityList);
                cnt++;
                if (cnt % 10000 == 0) {
                    Logger.info("Evaluated on {} groups.", cnt);
                }
            } else {
                skipped++;
            }
        }
        Logger.info("Evaluated on {} groups.", cnt);
        Logger.info("Skipped {} groups to evaluate on because of empty ground truth.", skipped);
        List<MetricResult> metricResults = EvaluatorUtilities.indexMetrics(predictor.getConfig(),
                requestContext, metrics, indexers);
        return new Evaluation(metricResults);
    }
}
