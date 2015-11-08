import java.awt.Image;
import java.awt.image.*;
import javax.imageio.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.DecimalFormat;

public class Exif {
	private static int getLongValue(byte[] bFile, int i) {
		int value = 0;
		int mask = 0xFF;
		for (int k = 0; k < 4; ++k) {
			value += ((int)(bFile[i + k] & mask) << (8 * k));
		}
		return value;
	}
	private static double[] transDegree(double d) {
		double degree = (double)(int)d;
		d -= degree;
		d *= 60;
		double minute = (double)(int)d;
		d -= minute;
		d *= 60;
		DecimalFormat df = new DecimalFormat("#.0000");
		double second = Double.parseDouble(df.format(d));
		double[] result = {degree, minute, second};
		return result;
	}
	private static int[] getFraction(double d) {
		int[] fract = new int[2];
		double[] temp = new double[2];
		temp[0] = d;
		temp[1] = 1.0;
		while (Math.abs((double)(int)temp[0] - temp[0]) > 1E-5) {
			temp[0] *= 10;
			temp[1] *= 10;
		}
		fract[0] = (int)temp[0];
		fract[1] = (int)temp[1];
		return fract;
	}
	private static byte[] getLongBytes(int i) {
		byte[] result = new byte[4];
		for (int k = 0; k < 4; ++k) {
			result[k] = (byte)(i >>> (k * 8));
		}
		return result;
	}
	
	private static class GpsLocation {
		public double longitude;
		public char longRef;
		public double latitude;
		public char latiRef;
	}
	private static Boolean validLocation(GpsLocation loc) {
		if (loc.longitude >= 0.0 && loc.longitude <= 180.0) {
			if (loc.latitude >= 0.0 && loc.latitude <= 90.0) {
				if (loc.longRef == 'E' || loc.longRef == 'W') {
					if (loc.latiRef == 'N' || loc.latiRef == 'S') {
						return true;
					}
				}
			}
		}
		return false;
	}
	public static void changeLocation(String filename, GpsLocation loc) {
		FileInputStream fileInputStream = null;
		File file = new File(filename);
		byte[] bFile = new byte[(int)file.length()];
		//double[] longitude = transDegree(loc.longitude);
		//double[] latitude = transDegree(loc.latitude);
		try {
			fileInputStream = new FileInputStream(file);
			fileInputStream.read(bFile);
			fileInputStream.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		Boolean isHead = false;
		int ffcount = 0;
		for (int i = 0; i < bFile.length; i++) {
			if (isHead) {
				if (bFile[i] == (byte)0xE1) {
					int length = ((short)(bFile[i + 2]) << 8) + (short)bFile[i + 1];
					i += 3;
					String checkStr = "Exif";
					for (int k = 0; k < 4; ++k) {
						if ((char)bFile[i + k] != checkStr.charAt(k)) {
							break;
						}
					}
					i += 6;
					int tiffHeaderStart = i;
					i += 8;
					int ifdEntryNum = ((short)(bFile[i]) << 8) + (short)bFile[i + 1];
					i += 2;
					for (int k = 0; k < ifdEntryNum; ++k) {
						// Is Exif GPS data
						if (bFile[i] == (byte)0x25 && bFile[i + 1] == (byte)0x88) {
							int ifdType = ((short)(bFile[i + 3]) << 8) + (short)bFile[i + 2];
							//Is Long type
							if (ifdType == 4) {

								int gpsOffset = ((int)(bFile[i + 11]) << 24) + ((int)(bFile[i + 10]) << 16) + ((int)(bFile[i + 9]) << 8) + ((int)bFile[i + 8] & 0xFF);
								int gpsStart = gpsOffset + tiffHeaderStart;
								i = gpsStart;
								int gpsItemNum = ((short)(bFile[i + 1]) << 8) + (short)bFile[i];
								i += 2;
								for (int t = 0; t < gpsItemNum; ++t) {
									int tagid = ((short)(bFile[i + 1]) << 8) + (short)bFile[i];
									int type = ((short)(bFile[i + 3]) << 8) + (short)bFile[i + 2];
									int count = ((int)(bFile[i + 7]) << 24) + ((int)(bFile[i + 6]) << 16) + ((int)(bFile[i + 5]) << 8) + ((int)bFile[i + 4] & 0xFF);
									if (tagid == 1) {
										if (type == 2 && count == 2) {
											bFile[i + 8] = (byte)loc.latiRef;
											char ref = (char)bFile[i + 8];
											//System.out.println(ref);
										}
									} else if (tagid == 2) {
										if (type == 5 && count == 3) {
											int offset = getLongValue(bFile, i + 8);
											int degreeStart = tiffHeaderStart + offset;
											//int temp = degreeStart;
											double[] latitude = transDegree(loc.latitude);
											for (int x = 0; x < 3; ++x) {
												int[] ration = getFraction(latitude[x]);
												for (int j = 0; j < 2; ++j) {
													byte[] bytes = getLongBytes(ration[j]);
													for (int y = 0; y < 4; ++y) {
														bFile[degreeStart + y] = bytes[y];
													}
													degreeStart += 4;
												}
											}
											/*degreeStart = temp;
											double[] degrees = new double[3];
											for (int x = 0; x < 3; ++x) {
												int numer = getLongValue(bFile, degreeStart);
												int denom = getLongValue(bFile, degreeStart + 4);
												degrees[x] = (double)numer / (double)denom;
												System.out.println(degrees[x]);
												degreeStart += 8;
											}*/
										}
									} else if (tagid == 3) {
										if (type == 2 && count == 2) {
											bFile[i + 8] = (byte)loc.longRef;
											char ref = (char)bFile[i + 8];
											//System.out.println(ref);
										}
									} else if (tagid == 4) {
										if (type == 5 && count == 3) {
											int offset = getLongValue(bFile, i + 8);
											int degreeStart = tiffHeaderStart + offset;
											// int temp = degreeStart;
											double[] longitude = transDegree(loc.longitude);
											for (int x = 0; x < 3; ++x) {
												int[] ration = getFraction(longitude[x]);
												for (int j = 0; j < 2; ++j) {
													byte[] bytes = getLongBytes(ration[j]);
													for (int y = 0; y < 4; ++y) {
														bFile[degreeStart + y] = bytes[y];
													}
													degreeStart += 4;
												}
											}
											/*degreeStart = temp;
											double[] degrees = new double[3];
											for (int x = 0; x < 3; ++x) {
												int numer = getLongValue(bFile, degreeStart);
												int denom = getLongValue(bFile, degreeStart + 4);
												degrees[x] = (double)numer / (double)denom;
												System.out.println(degrees[x]);
												degreeStart += 8;
											}*/
										}
									}
									i += 12;
								}
							}
							break;
						}
						i += 12;
					}
				}
			}
			isHead = (bFile[i] == (byte)0xFF);
		}
		try {
			FileOutputStream fos = new FileOutputStream("output.jpg");
			fos.write(bFile);
			fos.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		
	}
	public static void main(String[] args) {
		if (args.length != 5) {
			return;
		}
		String file = args[0];
		GpsLocation loc = new GpsLocation();
		loc.longitude = Double.parseDouble(args[1]);
		loc.longRef = args[2].charAt(0);
		loc.latitude = Double.parseDouble(args[3]);
		loc.latiRef = args[4].charAt(0);
		if (validLocation(loc)) {
			changeLocation(file, loc);
		}
	}
}