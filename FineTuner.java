import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Base64;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.TextView;

public class FineTuner {

	private static final int PORT = 6000;
	
	private ServerSocket serverSocket;
	private Thread serverThread;

	private Activity activity;

	private static FineTuner instace;
	
	public static void registerActivity(Activity activity) {
		if(instace == null)
			instace = new FineTuner();
		instace.register(activity);
	}
	
	private void register(Activity activity) {
		this.activity = activity;
	}

	private FineTuner() {
		this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();
	}
	
	class ServerThread implements Runnable {

		public void run() {
			Socket socket = null;
			try {
				serverSocket = new ServerSocket(PORT);
			} catch (IOException e) {
				e.printStackTrace();
			}
			while (!Thread.currentThread().isInterrupted()) {

				try {

					socket = serverSocket.accept();

					CommunicationThread commThread = new CommunicationThread(socket);
					new Thread(commThread).start();

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	class CommunicationThread implements Runnable {

		private Socket clientSocket;
		private BufferedReader input;
		private BufferedOutputStream output;

		public CommunicationThread(Socket clientSocket) throws SocketException {
			this.clientSocket = clientSocket;

			try {
				this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
				this.output = new BufferedOutputStream(this.clientSocket.getOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					String read = input.readLine();
					if(read != null) {
						try {
							onData(new JSONObject(read));
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		private void send(String response, Object data) throws UnsupportedEncodingException, IOException {
			JSONObject toSend = new JSONObject();
			try {
				toSend.put("response", response);
				toSend.put("data", data);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			String payload = toSend.toString() + "\n";
			output.write(payload.getBytes("UTF-8"));
			output.flush();
		}
		
		private void onData(JSONObject json) throws JSONException, IOException {
			String request = json.getString("request");
			final View rootView = activity.getWindow().getDecorView();
			if(request.equals("getTree")) {
				send("tree", getViewDescription(rootView));
			} else if(request.equals("getImage")) {
				try {
					int hashcode = json.getInt("hashcode");
					View view = getViewForHashCode(rootView, hashcode);
					Bitmap bmp = loadBitmapFromView(view);
					bmp = scaleDown(bmp, 400, true);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();  
					bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos);   
					byte[] b = baos.toByteArray(); 
					String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
					send("image", "data:image/jpeg;base64, " + encodedImage);
				} catch (Throwable e) {
					send("image", "error");
				}
			} else if(request.equals("getValues")) {
				int hashcode = json.getInt("hashcode");
				View view = getViewForHashCode(rootView, hashcode);
				send("values", getViewValues(view));
			} else if(request.equals("newValues")) {
				final JSONArray array = json.getJSONArray("values");
				int hashcode = json.getInt("hashcode");
				final View view = getViewForHashCode(rootView, hashcode);
				view.post(new Runnable() {
					@Override
					public void run() {
						try {
							applyValue(view, array);
						} catch (JSONException e) {
							e.printStackTrace();
						}
						view.postInvalidate();
						view.post(new Runnable() {
							@Override
							public void run() {
								try {
									send("applied", "ok");
								} catch (UnsupportedEncodingException e) {
									e.printStackTrace();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						});
					}
				});
			}
		}

		private void applyValue(final View view, JSONArray array) throws JSONException {
			for (int i = 0; i < array.length(); i++) {
				JSONObject obj = array.getJSONObject(i);
				String key = obj.keys().next();
				final String value = obj.getString(key);
				try {
					// call function acording to value
					ViewApplier.class.getMethod(key, View.class, String.class).invoke(null, view, value);
				} catch (IllegalAccessException e) {
				} catch (IllegalArgumentException e) {
				} catch (InvocationTargetException e) {
				} catch (NoSuchMethodException e) {
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static class ViewApplier {
		
		public static void paddingLeft(View v, String value) {
			v.setPadding(Integer.parseInt(value), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom());
		}
		
		public static void paddingRight(View v, String value) {
			v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), Integer.parseInt(value), v.getPaddingBottom());
		}
		
		public static void paddingTop(View v, String value) {
			v.setPadding(v.getPaddingLeft(), Integer.parseInt(value), v.getPaddingRight(), v.getPaddingBottom());
		}
		
		public static void paddingBottom(View v, String value) {
			v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Integer.parseInt(value));
		}

		public static void text(View v, String value) {
			((TextView) v).setText(value);
		}
		
		public static void visibility(View v, String value) {
			v.setVisibility(Integer.parseInt(value));
		}
		
		public static void textSize(View v, String value) {
			((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_PX , Integer.parseInt(value));
		}
		
		public static void marginLeft(View v, String value) {
			MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
			lp.leftMargin = Integer.parseInt(value);
			v.setLayoutParams(lp);
		}
		
		public static void marginRight(View v, String value) {
			MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
			lp.rightMargin = Integer.parseInt(value);
			v.setLayoutParams(lp);
		}
		
		public static void marginTop(View v, String value) {
			MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
			lp.topMargin = Integer.parseInt(value);
			v.setLayoutParams(lp);
		}
		
		public static void marginBottom(View v, String value) {
			MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
			lp.bottomMargin = Integer.parseInt(value);
			v.setLayoutParams(lp);
		}
		
		public static void width(View v, String value) {
			ViewGroup.LayoutParams lp = v.getLayoutParams();
			lp.width = Integer.parseInt(value);
			v.setLayoutParams(lp);
		}
		
		public static void height(View v, String value) {
			ViewGroup.LayoutParams lp = v.getLayoutParams();
			lp.height = Integer.parseInt(value);
			v.setLayoutParams(lp);
		}
		
		public static void textColor(View v, String value) {
			((TextView) v).setTextColor(Color.parseColor("#" + value));
		}
		
		public static void backgroundColor(View v, String value) {
			v.setBackgroundColor(Color.parseColor("#" + value));
		}
		
	}
	
	public static Bitmap scaleDown(Bitmap realImage, float maxImageSize, boolean filter) {
	    float ratio = Math.min(
	            (float) maxImageSize / realImage.getWidth(),
	            (float) maxImageSize / realImage.getHeight());
	    int width = Math.round((float) ratio * realImage.getWidth());
	    int height = Math.round((float) ratio * realImage.getHeight());

	    Bitmap newBitmap = Bitmap.createScaledBitmap(realImage, width,
	            height, filter);
	    return newBitmap;
	}
	
	public static Bitmap loadBitmapFromView(View v) {
		Bitmap b = Bitmap.createBitmap(v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
		v.draw(c);
		return b;
	}
	
	private static View getViewForHashCode(View v, int hashcode) {
		if(v.hashCode() == hashcode)
			return v;
		else if(v instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) v;
			for (int i = 0; i < vg.getChildCount(); i++) {
				View toReturn = getViewForHashCode(vg.getChildAt(i), hashcode);
				if(toReturn != null)
					return toReturn;
			}
		}
		return null;
	}
	
	private static JSONObject getViewValues(View v) throws JSONException {
		JSONObject obj = new JSONObject();
		
		obj.put("hashcode", v.hashCode());
		
		obj.put("width", v.getLayoutParams().width);
		obj.put("height", v.getLayoutParams().height);
		obj.put("visibility", v.getVisibility());
		if (v.getBackground() instanceof ColorDrawable) {
			ColorDrawable color = (ColorDrawable) v.getBackground();
			obj.put("backgroundColor", String.format("%08X", color.getColor()));	
		}
		
		try {
			MarginLayoutParams pp = (MarginLayoutParams) v.getLayoutParams();
			obj.put("marginLeft", pp.leftMargin);
			obj.put("marginRight", pp.rightMargin);
			obj.put("marginTop", pp.topMargin);
			obj.put("marginBottom", pp.bottomMargin);
		} catch (Exception e) {}
		
		obj.put("paddingLeft", v.getPaddingLeft());
		obj.put("paddingRight", v.getPaddingRight());
		obj.put("paddingBottom", v.getPaddingBottom());
		obj.put("paddingTop", v.getPaddingTop());

		if (v instanceof TextView) {
			TextView tx = (TextView) v;
			obj.put("text", tx.getText());
			obj.put("textSize", ((TextView) v).getTextSize());
			obj.put("textColor", String.format("%08X", tx.getCurrentTextColor()));
		}
		
		return obj;
	}
	
	private static JSONObject getViewDescription(View v) throws JSONException {
		JSONObject obj = new JSONObject();
		obj.put("text", v.getClass().getSimpleName());
		
		try {
			String idName = v.getContext().getResources().getResourceEntryName(v.getId());
			obj.put("text", "@id/" + idName);
		} catch (Exception e) {}
		
		obj.put("hashcode", v.hashCode());
		
		if (v instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) v;
			JSONArray ja = new JSONArray();
			for (int i = 0; i < group.getChildCount(); i++) {
				ja.put(getViewDescription(group.getChildAt(i)));
			}
			obj.put("children", ja);
		}
		return obj;
	}
	
	
}
