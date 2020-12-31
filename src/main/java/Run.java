import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

import res.http.HTTPServer;
import res.http.HTTPServer.*;

public class Run {
	
	// Grabs the pixels from a buffered image and converts them to our specified format
	static String getPixels(BufferedImage img) {
		StringBuilder pixelString = new StringBuilder();
		
	      for (int y = 0; y < img.getHeight(); y++) {
	         for (int x = 0; x < img.getWidth(); x++) {
	            //Retrieving contents of a pixel
	            int pixel = img.getRGB(x,y);
	            //Creating a Color object from pixel value
	            Color color = new Color(pixel, true);
	            //Retrieving the R G B values
	            int red = color.getRed();
	            int green = color.getGreen();
	            int blue = color.getBlue();
	            pixelString.append(red+":");
	            pixelString.append(green+":");
	            pixelString.append(blue+"");
	            pixelString.append(",");
	         }
	         pixelString.append("\n");
	      }
		
	return pixelString.toString();
	}
	
	// Mokiros revealed quite a few image processing techniques/algorithms for use here!
	static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
	    BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
	    Graphics2D graphics2D = resizedImage.createGraphics();
	    graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
	    graphics2D.dispose();
	    return resizedImage;
	}
	// uses modificed lanczos formula
	static BufferedImage scaledInstanceResize(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
	    Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT);
	    BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
	    outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
	    return outputImage;
	}
	// Smooth image with some quality loss
	static BufferedImage LanczosResize(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
		ResampleOp resizeOp = new ResampleOp(targetWidth, targetHeight);
		resizeOp.setFilter(ResampleFilters.getLanczos3Filter());
		BufferedImage scaledImage = resizeOp.filter(originalImage, null);
		return scaledImage;
	}
	// Accepts directly the algorithm and compression methods as outlines in java docs
	static BufferedImage anyResize(BufferedImage source, int targetWidth, int destHeight, Object interpolation)
	{
	    BufferedImage bicubic = new BufferedImage(targetWidth, destHeight, source.getType());
	    Graphics2D bg = bicubic.createGraphics();
	    bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
	    float sx = (float)targetWidth / source.getWidth();
	    float sy = (float)destHeight / source.getHeight();
	    bg.scale(sx, sy);
	    bg.drawImage(source, 0, 0, null);
	    bg.dispose();
	    return bicubic;
	}
	
	public static void main(String[] args) {

		// This is where the port for the HTTPServer is specified (Should be a .properties file probably)
        HTTPServer server = new HTTPServer(3074);
        
        VirtualHost host = server.getVirtualHost(null); // default host
        host.setAllowGeneratedIndex(true); // with directory index pages
        host.addContext("/", new ContextHandler() {
            public int serve(Request req, Response resp) throws IOException {
                resp.getHeaders().add("Content-Type", "text/plain");
                resp.send(200, "This is the index. I think you were looking for /api/?");
                return 0;
            }
        });
        host.addContext("/api/", new ContextHandler() {
            public int serve(Request req, Response resp) throws IOException {
                resp.getHeaders().add("Content-Type", "text/plain");
                resp.send(400, "We have a few APIs... What are you looking for here?");
                return 0;
            }
        });
        host.addContext("/api/getimage", new ContextHandler() {
            public int serve(Request req, Response resp) throws IOException {
            	Map<String, String> params = req.getParams();
        		
            	String resolutionOverride = "default";
            	String resolution = "177x72";
            	
        		for (Map.Entry<String, String> entry : params.entrySet()) {
            	    if(entry.getKey().equals("resizeMode")) {
            	    	resolutionOverride = entry.getValue().toString(); // Hopefully this is "F"
            	    } else if(entry.getKey().equals("resolution")) {
            	    	resolution = entry.getValue().toString(); 
            	    } else	{
            	    	System.out.println("Unhandled param type:" + entry.getKey());
            	    }
            	}
        		
        		// do resolution parsing
        		String[] splitRes = resolution.split("x");
            	
        		if(splitRes.length != 2)
        			resp.send(400, "{\"error\":\"bad resolution format (177x72)\"}");
        		
        		int resX = Integer.parseInt(splitRes[0]);
        		int resY = Integer.parseInt(splitRes[1]);
        		
        		Robot robot = null;
        		
        		try {
        			robot = new Robot();
        		} catch (AWTException e) {
        			// TODO Auto-generated catch block
        			e.printStackTrace();
        		}
            	
        		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        		Rectangle screenRectangle = new Rectangle(screenSize);
        		BufferedImage image = robot.createScreenCapture(screenRectangle);
        		
        		switch (resolutionOverride) {
        			case "scaled":
	        			//System.out.println("Using scaled downsize at "+resolution);
	        			image = scaledInstanceResize(image, resX, resY); // Using java.awt Graphics2d resize
        			
        			case "lanczos":
	        			//System.out.println("Using lanczos downsize at "+resolution);
	        			image = LanczosResize(image, resX, resY); // Using java.awt Graphics2d resize
	        			
        			case "bicubic":

	        			//System.out.println("Using bicubic downsize at "+resolution);
	        			image = anyResize(image, resX, resY, RenderingHints.VALUE_INTERPOLATION_BICUBIC); // Using java.awt Graphics2d bicubic resize
	        			
        			case "bilinear":

	        			//System.out.println("Using bilinear downsize at "+resolution);
	        			image = anyResize(image, resX, resY, RenderingHints.VALUE_INTERPOLATION_BILINEAR); // Using java.awt Graphics2d bilinear resize
	        			
        			case "nearestneighbor":

	        			//System.out.println("Using nearest neighbor downsize at "+resolution);
	        			image = anyResize(image, resX, resY, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR); // Using java.awt Graphics2d nearest neighbor resize
	        			
        			default:
	        			//System.out.println("Using default downsize at "+resolution);
	        			image = resizeImage(image, resX, resY); // Using java.awt Graphics2d resize
        		}
        		
        		String pixelData = getPixels(image);
                
                resp.getHeaders().add("Content-Type", "text/plain");
                resp.send(200, pixelData);
                return 0;
            }
        });
        
        try {
			server.start();
	        System.out.println("HTTPServer is running.");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
