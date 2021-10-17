package com.radio.codec2talkie.tools;

public class RadioTools {

    public static int calculateLoraSpeedBps(int bw, int sf, int cr) {
        return (int)(sf * (4.0 / cr) / (Math.pow(2.0, sf) / bw));
    }

    public static double calculateLoraSensitivity(int bw, int sf) {
        double snrLimit = -7.0;
        double noiseFigure = 6.0;
        switch (sf) {
            case 7:
                snrLimit = -7.5;
                break;
            case 8:
                snrLimit = -10.0;
                break;
            case 9:
                snrLimit = -12.6;
                break;
            case 10:
                snrLimit = -15.0;
                break;
            case 11:
                snrLimit = -17.5;
                break;
            case 12:
                snrLimit = -20.0;
                break;
        }
        return (-174 + 10 * Math.log10(bw) + noiseFigure + snrLimit);
    }
}

