package com.taraxippus.bgm.gl;

import android.content.*;
import android.graphics.*;
import android.media.audiofx.*;
import android.opengl.*;
import android.os.*;
import android.preference.*;
import android.view.*;
import android.view.Display.*;
import java.nio.*;
import javax.microedition.khronos.opengles.*;

import android.opengl.Matrix;
import com.taraxippus.bgm.BGMService;

public class GLRenderer implements GLSurfaceView.Renderer, Visualizer.OnDataCaptureListener
{
	
	@Override
	public void onWaveFormDataCapture(Visualizer p1, byte[] p2, int p3)
	{
	}

	@Override
	public void onFftDataCapture(Visualizer p1, byte[] p2, int p3)
	{
		System.arraycopy(p2, 0, fft, 0, p2.length);
	}

	public final int COUNT = 64;
	public final int LINE_COUNT = 16;
	public final float BAR_WIDTH = 0.25F;
	public final float BAR_SPACE = 0.05F;
	public final float TILT = 0.25F;
	
	private final String vertexShader =
	"#version 100\n" +
	"uniform mat4 u_MVP;" +
	"uniform float u_Height[" + COUNT + "];" +
	"attribute vec4 a_Position;" +
	"varying vec4 v_Position;" +
	"void main() {" +
	"  v_Position = vec4(a_Position.x, a_Position.y * (u_Height[int(a_Position.w)] + 0.1), a_Position.z * (u_Height[int(a_Position.w)] + 0.1), 1.0);" +
	"  gl_Position = u_MVP * v_Position;" +
	"}";

	private final String vertexShader_circle =
	"#version 100\n" +
	"uniform mat4 u_MVP;" +
	"uniform float u_Height[" + COUNT + "];" +
	"attribute vec4 a_Position;" +
	"varying vec4 v_Position;" +
	"void main() {" +
	"  v_Position = vec4(a_Position.x, a_Position.y * (u_Height[int(a_Position.w)] + 0.1), a_Position.z, 1.0);" +
	"  gl_Position = u_MVP * v_Position;" +
	"}";

	private final String fragmentShader =
	"#version 100\n" +
	"precision mediump float;" +
	"uniform vec4 u_Color;" +
	"varying vec4 v_Position;" +
	"void main() {" +
	"  gl_FragColor = vec4(u_Color.rgb, v_Position.y < 0.0 ? clamp(v_Position.y * 0.25 + 0.75, 0.0, 1.0) * 0.9 : 1.0);" +
	"}";

	private final String vertexShader_circle_rainbow =
	"#version 100\n" +
	"uniform mat4 u_MVP;" +
	"uniform float u_Height[" + COUNT + "];" +
	"attribute vec4 a_Position;" +
	"varying vec4 v_Position;" +
	"varying vec3 v_Color;" +

	"vec3 hsv2rgb(vec3 c)" +
	"{" +
	"  vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);" +
	"  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);" +
	"  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);" +
	"}" +

	"void main() {" +
	"  v_Position = vec4(a_Position.x, a_Position.y * (u_Height[int(a_Position.w)] + 0.1), a_Position.z, 1.0);" +
	"  v_Color = hsv2rgb(vec3(a_Position.w / " + COUNT + ".0, 1.0, 1.0));" +
	"  gl_Position = u_MVP * v_Position;" +
	"}";

	private final String vertexShader_rainbow =
	"#version 100\n" +
	"uniform mat4 u_MVP;" +
	"uniform float u_Height[" + COUNT + "];" +
	"attribute vec4 a_Position;" +
	"varying vec4 v_Position;" +
	"varying vec3 v_Color;" +

	"vec3 hsv2rgb(vec3 c)" +
	"{" +
	"  vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);" +
	"  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);" +
	"  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);" +
	"}" +

	"void main() {" +
	"  v_Position = vec4(a_Position.x, a_Position.y * (u_Height[int(a_Position.w)] + 0.1), a_Position.z * (u_Height[int(a_Position.w)] + 0.1), 1.0);" +
	"  v_Color = hsv2rgb(vec3(a_Position.w / " + COUNT + ".0, 1.0, 1.0));" +
	"  gl_Position = u_MVP * v_Position;" +
	"}";

	private final String fragmentShader_rainbow =
	"#version 100\n" +
	"precision mediump float;" +
	"uniform vec4 u_Color;" +
	"varying vec4 v_Position;" +
	"varying vec3 v_Color;" +
	"void main() {" +
	"  gl_FragColor = vec4(v_Color.rgb, v_Position.y < 0.0 ? clamp(v_Position.y * 0.25 + 0.75, 0.0, 1.0) * 0.9 : 1.0);" +
	"}";

	final float[] height_bars = new float[COUNT];
	final byte[] fft = new byte[COUNT * 2];

	final float[] projection = new float[16];
	final float[] view = new float[16];
	final float[] model = new float[16];

	final float[] mvp = new float[16];

	final Shape bars = new Shape();
	final Program program = new Program();

	final Context context;
	
	int mvpHandle;
	int heightHandle;

	public GLRenderer(Context context)
	{
		super();	
		
		this.context = context;
	}

	@Override
	public void onSurfaceCreated(GL10 p1, javax.microedition.khronos.egl.EGLConfig p2)
	{
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		
		boolean mirrored = preferences.getBoolean("mirror", false);
		boolean rainbow = preferences.getBoolean("rainbow", false);
		boolean circle = preferences.getBoolean("circle", false);
		
		FloatBuffer bars_vertices = FloatBuffer.allocate(COUNT * 4 * (!circle ? 8 : 4) * (mirrored ? 2 : 1));

		if (circle)
		{
			float radius = COUNT * (BAR_WIDTH + BAR_SPACE) / (float)Math.PI / 2F;
			float angle, angle2;

			for (int i = 0; i < COUNT; ++i)
			{
				angle = (float)i / COUNT * (float)Math.PI * (mirrored ? 1 : 2);
				angle2 = (i * (BAR_WIDTH + BAR_SPACE) + BAR_WIDTH) / (COUNT * (BAR_SPACE + BAR_WIDTH)) * (float)Math.PI * (mirrored ? 1 : 2);

				bars_vertices.put((float) Math.cos(angle) * radius);
				bars_vertices.put(-0.5F);
				bars_vertices.put((float) Math.sin(angle) * radius);
				bars_vertices.put(i);

				bars_vertices.put((float) Math.cos(angle2) * radius);
				bars_vertices.put(-0.5F);
				bars_vertices.put((float) Math.sin(angle2) * radius);
				bars_vertices.put(i);

				bars_vertices.put((float) Math.cos(angle) * radius);
				bars_vertices.put(1F);
				bars_vertices.put((float) Math.sin(angle) * radius);
				bars_vertices.put(i);

				bars_vertices.put((float) Math.cos(angle2) * radius);
				bars_vertices.put(1F);
				bars_vertices.put((float) Math.sin(angle2) * radius);		
				bars_vertices.put(i);
			}

			if (mirrored)
			{

				for (int i = 0; i < COUNT; ++i)
				{
					angle = (float)Math.PI + (float)i / COUNT * (float)Math.PI;
					angle2 = (float)Math.PI + (i * (BAR_WIDTH + BAR_SPACE) + BAR_WIDTH) / (COUNT * (BAR_SPACE + BAR_WIDTH)) * (float)Math.PI;

					bars_vertices.put((float) Math.cos(angle) * radius);
					bars_vertices.put(-0.5F);
					bars_vertices.put((float) Math.sin(angle) * radius);
					bars_vertices.put(COUNT - i - 1);

					bars_vertices.put((float) Math.cos(angle2) * radius);
					bars_vertices.put(-0.5F);
					bars_vertices.put((float) Math.sin(angle2) * radius);
					bars_vertices.put(COUNT - i - 1);

					bars_vertices.put((float) Math.cos(angle) * radius);
					bars_vertices.put(1F);
					bars_vertices.put((float) Math.sin(angle) * radius);
					bars_vertices.put(COUNT - i - 1);

					bars_vertices.put((float) Math.cos(angle2) * radius);
					bars_vertices.put(1F);
					bars_vertices.put((float) Math.sin(angle2) * radius);		
					bars_vertices.put(COUNT - i - 1);
				}
			}
		}
		else
		{
			for (int i = -COUNT / 2; i < COUNT / 2; ++i)
			{
				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) - BAR_WIDTH / 2F);
				bars_vertices.put(-0.5F);
				bars_vertices.put(-0.5F * TILT);
				bars_vertices.put(i + COUNT / 2);

				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) + BAR_WIDTH / 2F);
				bars_vertices.put(-0.5F);
				bars_vertices.put(-0.5F * TILT);
				bars_vertices.put(i + COUNT / 2);

				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) - BAR_WIDTH / 2F);
				bars_vertices.put(0);
				bars_vertices.put(0);
				bars_vertices.put(i + COUNT / 2);

				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) + BAR_WIDTH / 2F);
				bars_vertices.put(0);
				bars_vertices.put(0);
				bars_vertices.put(i + COUNT / 2);

				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) - BAR_WIDTH / 2F);
				bars_vertices.put(0);
				bars_vertices.put(0);
				bars_vertices.put(i + COUNT / 2);

				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) + BAR_WIDTH / 2F);
				bars_vertices.put(0);
				bars_vertices.put(0);
				bars_vertices.put(i + COUNT / 2);

				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) - BAR_WIDTH / 2F);
				bars_vertices.put(1F);
				bars_vertices.put(-1 * TILT);
				bars_vertices.put(i + COUNT / 2);

				bars_vertices.put((i + 0.5F) * (BAR_SPACE + BAR_WIDTH) + BAR_WIDTH / 2F);
				bars_vertices.put(1F);
				bars_vertices.put(-1 * TILT);		
				bars_vertices.put(i + COUNT / 2);
			}
		}

		ShortBuffer bars_indices = ShortBuffer.allocate(COUNT * 12 * (!circle ? 2 : 1) * (mirrored ? 2 : 1));

		for (int i = 0; i < COUNT * (!circle ? 2 : 1) * (mirrored ? 2 : 1); ++i)
		{
			bars_indices.put((short) (i * 4));	
			bars_indices.put((short) (i * 4 + 1));	
			bars_indices.put((short) (i * 4 + 2));	

			bars_indices.put((short) (i * 4 + 1));	
			bars_indices.put((short) (i * 4 + 3));	
			bars_indices.put((short) (i * 4 + 2));	


			bars_indices.put((short) (i * 4));	
			bars_indices.put((short) (i * 4 + 2));	
			bars_indices.put((short) (i * 4 + 1));	

			bars_indices.put((short) (i * 4 + 1));	
			bars_indices.put((short) (i * 4 + 2));	
			bars_indices.put((short) (i * 4 + 3));	
		}

		bars.init(GLES20.GL_TRIANGLES, bars_vertices, bars_indices, false, 4);	
		
		if (rainbow)
			program.init(circle ? vertexShader_circle_rainbow : vertexShader_rainbow, fragmentShader_rainbow, "a_Position");
		
		else
			program.init(circle ? vertexShader_circle : vertexShader, fragmentShader, "a_Position");

		int color = Color.parseColor(preferences.getString("color", "#ffffff"));
	
		program.use();
		GLES20.glUniform4f(program.getUniform("u_Color"), Color.red(color) / 255F, Color.green(color) / 255F, Color.blue(color) / 255F, 1);
		mvpHandle = program.getUniform("u_MVP");
		heightHandle = program.getUniform("u_Height");

		GLES20.glClearColor(0, 0, 0, 0F);
		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
		
		if (preferences.getBoolean("circle", false))
			Matrix.setLookAtM(view, 0, preferences.getFloat("cameraX", 0), preferences.getFloat("cameraZ", 10), preferences.getFloat("cameraY", 0), 0, 0, 0, 0, 0, 1);
		else
			Matrix.setLookAtM(view, 0, preferences.getFloat("cameraX", 0), preferences.getFloat("cameraY", 0), preferences.getFloat("cameraZ", 10), 0, 0, 0, 0, 1, 0);

		update = false;
	}	

	int width, height;

	@Override
	public void onSurfaceChanged(GL10 p1, int width, int height)
	{
		this.width = width;
		this.height = height;

		GLES20.glViewport(0, 0, width, height);

		float ratio = (float) height / width;
		Matrix.frustumM(projection, 0, -1, 1, -ratio, ratio, 1, 100);
	}

	boolean update;

	@Override
	public void onDrawFrame(GL10 p1)
	{		
		if (!bars.initialized() || !program.initialized())
		{
			System.err.println("Not initialized!");
			return;
		}

		for (int i = 0; i < COUNT; ++i)
		{
			if (i == 0)
				height_bars[0] = height_bars[0] * 0.75F + 0.25F * 2 * nanToZero(Math.log10(fft[0] * fft[0] * 2));
			else
				height_bars[i] = height_bars[i] * 0.75F + 0.25F * 2 * nanToZero(Math.log10(Math.abs(fft[i * 2] * fft[i * 2] + fft[i * 2 + 1] * fft[i * 2 + 1])));

		}

		GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

		program.use();

		Matrix.setIdentityM(model, 0);
		Matrix.multiplyMM(mvp, 0, view, 0, model, 0);
		Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0);

		GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
		GLES20.glUniform1fv(heightHandle, COUNT, height_bars, 0);

		bars.render();
	}
	
	public float nanToZero(double f)
	{
		return Float.isInfinite((float)f) || Float.isNaN((float)f) ? 0 :(float) f;
	}
}
