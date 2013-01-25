package edu.macalester.wpsemsim.matrix;

import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.utils.EnvConfigurator;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.Env;
import edu.macalester.wpsemsim.utils.KnownSim;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

//outputs file in format: phrase1 \t phrase2 \t metric_score \t gold_score

public class TSVMatrixWriter {
    public static Logger LOG = Logger.getLogger(TSVMatrixWriter.class.getName());

    public TSVMatrixWriter(File goldStandard) throws  IOException{
        this.gold = KnownSim.read(goldStandard);
    }

    private List<KnownSim> gold;

    public void writeMatrix(SimilarityMetric metric, BufferedWriter writer) throws IOException, ParseException {
        for (int i = 0; i<gold.size();i++){
            final KnownSim ks = gold.get(i);
            double sim =metric.similarity(ks.phrase1, ks.phrase2);
            if (!Double.isInfinite(sim) && !Double.isNaN(sim)) {
                String line = ks.phrase1+"\t"+ks.phrase2+"\t"+ sim + "\t"+ks.similarity+"\n";
                writer.write(line);
            }
        }
    }

    public void writeAllMatrix(BufferedWriter writer, EnvConfigurator conf)throws ParseException,ConfigurationFile.ConfigurationException,IOException {
        String[] metricNames = new String[]{"article-cats","article-text","article-links","esa"};
        SimilarityMetric[] metrics = new SimilarityMetric[metricNames.length];
        for (int i=0;i<metricNames.length;i++){
            metrics[i]=conf.loadMetric(metricNames[i]);
        }
        String line = "gold";
        for (String s:metricNames){
            line=line+"\t"+s;
        }
        line=line+"\n";
        writer.write(line);
        for (int i=0;i<gold.size();i++){
            KnownSim ks =gold.get(i);
            line=""+ks.similarity;
            for (SimilarityMetric m:metrics){
                line=line+"\t"+m.similarity(ks.phrase1,ks.phrase2);
            }
            line=line+"\n";
            writer.write(line);
        }
    }

    //TSVMatrixWriter conf gold out metric
    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException {
        EnvConfigurator conf = new EnvConfigurator(
                new ConfigurationFile(new File(args[0])));
        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();
        TSVMatrixWriter twrite = new TSVMatrixWriter(
                new File(args[1])
        );
        BufferedWriter writer = new BufferedWriter(new FileWriter(args[2]));
        if (args[3].equals("all")){
            try{
                twrite.writeAllMatrix(writer, conf);
            } catch (Exception e){
                LOG.log(Level.SEVERE, e.toString());
            }
        } else {
            SimilarityMetric metric = conf.loadMetric(args[3]);
            try{
                twrite.writeMatrix(metric, writer);
            } catch (Exception e){
                LOG.log(Level.SEVERE, e.toString());
            }
        }
        writer.close();
    }
}
