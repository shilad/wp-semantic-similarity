package edu.macalester.wpsemsim.sim.utils;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixTransposer;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseCosineSimilarity;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseSimilarityWriter;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.Env;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;

public class SimilarityMetricBuilder extends EnvConfigurator {
    private static final Logger LOG = Logger.getLogger(SimilarityMetricBuilder.class.getName());

    public SimilarityMetricBuilder(ConfigurationFile conf) {
        super(conf);
        super.setDoEnsembles(false);
        super.setDoPairwise(false);
    }

    public Env build() throws IOException, ConfigurationFile.ConfigurationException, InterruptedException {
        // load everything
        loadEnv();

        // build pairwise
        int wpIds[] = env.getMainIndex().getWpIds();
        for (String key : configuration.getKeys("metrics")) {
            JSONObject params = configuration.get("metrics", key);
            if (params.get("type").equals("pairwise")) {
                buildPairwise(key, params, wpIds);
            }
        }

        return env;
    }

    protected SimilarityMetric buildPairwise(String name, JSONObject params, int[] wpIds) throws ConfigurationFile.ConfigurationException, IOException, InterruptedException {
        info("building metric " + name);
        String basedOnName = requireString(params, "basedOn");
        SimilarityMetric basedOn = env.getMetric(basedOnName);
        if (basedOn == null) {
            throw new ConfigurationFile.ConfigurationException("could not find basedOn metric: " + basedOnName);
        }

        File matrixFile = new File(requireString(params, "matrix"));
        File transposeFile = new File(requireString(params, "transpose"));
        PairwiseSimilarityWriter writer =
                new PairwiseSimilarityWriter(basedOn, matrixFile);
        writer.writeSims(wpIds, 1, 20);
        SparseMatrix matrix = new SparseMatrix(matrixFile);
        SparseMatrixTransposer transposer = new SparseMatrixTransposer(matrix, transposeFile, 100);
        transposer.transpose();
        SparseMatrix transpose = new SparseMatrix(transposeFile);

        SimilarityMetric metric = new PairwiseCosineSimilarity(matrix, transpose);
        metric.setName(name);
        env.addMetric(name, metric);

        return metric;
    }


    private void info(String message) {
        LOG.info("configurator for " + configuration.getPath() + ": " + message);
    }
}
