package com.lauszus.bioonpaper;

import com.lauszus.bioonpaper.jama.Matrix;

public interface Metric {
	double getDistance(Matrix a, Matrix b);
}
