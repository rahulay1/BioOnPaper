package com.lauszus.bioonpaper;


public class KNN {

	static float getDistance(String[] template, byte[] testFace) {
		float distance =0;
		for (int i=0; i<128;i++){
			distance = distance + (Float.parseFloat(template[i])-testFace[i])*(Float.parseFloat(template[i])-testFace[i]);
		}
		return distance;
	}

	// get the class label by using neighbors
}
