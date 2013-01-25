package edu.macalester.wpsemsim.sim.utils;

import edu.macalester.wpsemsim.sim.Interpolator;
import edu.macalester.wpsemsim.utils.ConfigurationFile;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: aaron
 * Date: 1/22/13
 * Time: 1:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class InterpolatorModelBuilder{
    //args gold, conf, metric, out
    public static void main(String args[]) throws ConfigurationFile.ConfigurationException,IOException {
        Interpolator interp= new Interpolator(args[0],args[1],args[2]);
        interp.mkModel();
        interp.writeModel(args[3]);
    }
}
