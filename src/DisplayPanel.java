/*
        This program is free software; you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation; either version 2 of the License, or
        (at your option) any later version.
        
        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.
        
        You should have received a copy of the GNU General Public License
        along with this program; if not, write to the Free Software
        Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
        MA 02110-1301, USA.
*/

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * A panel which can display a calculated fractal.
 */
public class DisplayPanel extends JPanel
{
	protected ParameterStack paramStack = null;
	protected FractalRenderer.Job drawIt = null;
	protected Point mouseStart = null;
	protected Point mouseEnd   = null;
	protected Point boxStart = null;
	protected Point boxEnd   = null;

	protected final int DRAG_NONE     = -1;
	protected final int DRAG_ZOOM_BOX = 0;
	protected final int DRAG_PAN      = 1;
	protected int typeOfDrag = DRAG_NONE;
	public boolean boxKeepsRatio = true;
	public boolean showCrosshairs = true;

	protected long displayStamp = 0;
	protected long lastStamp = -1;

	protected int runningJobs = 0;

	private boolean dragHasPushed = false;

	/**
	 * Build the component and register listeners
	 */
	public DisplayPanel(ParameterStack p, final Runnable onChange)
	{
		super();

		// This will be the place where our settings live.
		paramStack = p;

		// onResize
		addComponentListener(new ComponentListener() 
		{  
			public void componentResized(ComponentEvent evt)
			{
				Component c = (Component)evt.getSource();

				// Get new size
				Dimension newSize = c.getSize();

				// A "resized" event will trigger a recalculation of the fractal.
				// This does *NOT* create an undo record.
				paramStack.get().updateSize(newSize);
				onChange.run();
				dispatchRedraw();
			}
		
			public void componentHidden(ComponentEvent e) {}
			public void componentMoved(ComponentEvent e) {}
			public void componentShown(ComponentEvent e) {}
		});

		// Mouse Events
		MouseAdapter m = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				dragHasPushed = false;

				if (e.getButton() == MouseEvent.BUTTON1)
				{
					// Start creating a zooming-box.

					//System.out.println(e);
					mouseStart = e.getPoint();

					typeOfDrag = DRAG_ZOOM_BOX;
				}
				else if (e.getButton() == MouseEvent.BUTTON2)
				{
					// Start dragging the viewport.
					
					//System.out.println(e);
					mouseStart = e.getPoint();

					typeOfDrag = DRAG_PAN;
				}
				else if (e.getButton() == MouseEvent.BUTTON3)
				{
					// Directly center the viewport.

					paramStack.push();
					paramStack.get().updateCenter(e.getPoint());
					onChange.run();
					dispatchRedraw();
				}
				else
				{
					typeOfDrag = DRAG_NONE;
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				// Zoom-Box
				if (typeOfDrag == DRAG_ZOOM_BOX)
				{
					//System.out.println(e);

					// Only update if the mouse has been dragged *inside* the window
					if (mouseEnd != null
							&& mouseEnd.getX() >= 0 && mouseEnd.getX() < getWidth()
							&& mouseEnd.getY() >= 0 && mouseEnd.getY() < getHeight())
					{
						paramStack.push();
						paramStack.get().zoomBox(boxStart, boxEnd);
						onChange.run();
						dispatchRedraw();	
					}
				}

				mouseStart = null;
				mouseEnd   = null;
				typeOfDrag = DRAG_NONE;

				repaint();
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				// Zoom-Box
				if (typeOfDrag == DRAG_ZOOM_BOX)
				{
					//System.out.println(e);
					mouseEnd = e.getPoint();
					calcZoomBox();
					repaint();
				}
				
				// Pan/Move
				else if (typeOfDrag == DRAG_PAN)
				{
					// Only save the first change
					if (!dragHasPushed)
					{
						dragHasPushed = true;
						paramStack.push();
					}

					paramStack.get().updateCenter(mouseStart, e.getPoint());
					onChange.run();
					dispatchRedraw();

					mouseStart = e.getPoint();
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				paramStack.push();

				//System.out.println(e.getWheelRotation());
				if (e.getWheelRotation() == 1)
					paramStack.get().zoomIn();
				else
					paramStack.get().zoomOut();

				onChange.run();
				dispatchRedraw();
			}
		};
		addMouseListener(m);
		addMouseMotionListener(m);
		addMouseWheelListener(m);
	}

	/**
	 * Calc upper left and lower right point of the zoom box.
	 */
	private void calcZoomBox()
	{
		int w, h;

		w = Math.abs(mouseStart.x - mouseEnd.x);

		if (boxKeepsRatio)
		{
			double ratio = (double)getWidth() / (double)getHeight();
			h = (int)((double)w / ratio);
		}
		else
			h = Math.abs(mouseStart.y - mouseEnd.y);

		boxStart = new Point(mouseStart.x - w, mouseStart.y - h);
		boxEnd   = new Point(mouseStart.x + w, mouseStart.y + h);
	}

	/**
	 * Get the next stamp. No need for sync as this is always executed on the EDT.
	 */
	protected long nextStamp()
	{
		displayStamp++;
		displayStamp %= Long.MAX_VALUE;
		return displayStamp;
	}

	/**
	 * Check if this is the latest stamp. No need for sync as this is always executed on the EDT.
	 */
	protected boolean checkStamp(long s)
	{
		if (s > lastStamp)
		{
			lastStamp = s;
			return true;
		}
		else
		{
			return false;
		}
	}

	/**
	 * Dispatch a job which renders the fractal to the panel.
	 */
	public void dispatchRedraw()
	{
		runningJobs++;
		repaint();

		FractalRenderer.dispatchJob(Multifrac.numthreads,
				new FractalRenderer.Job(paramStack.get(), 1, nextStamp(), null),
				new FractalRenderer.Callback()
				{
					@Override
					public void run()
					{
						FractalRenderer.Job result = getJob();
						if (checkStamp(result.stamp))
							drawIt = result;

						runningJobs--;

						paintImmediately(0, 0, result.getWidth(), result.getHeight());
					}
				},
				null);
	}

	/**
	 * Draw stuff
	 */
	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D g2 = (Graphics2D)g;

		// Draw an image only if there's one available
		if (drawIt != null)
		{
			Image img = createImage(
						new MemoryImageSource(
							drawIt.getWidth(), drawIt.getHeight(), drawIt.getPixels(), 0, drawIt.getWidth()));
			g2.drawImage(img, new java.awt.geom.AffineTransform(1f, 0f, 0f, 1f, 0, 0), null);
		}

		// Draw the box only if zoom-box-dragging is in process
		if (typeOfDrag == DRAG_ZOOM_BOX && mouseStart != null && mouseEnd != null)
		{
			int x, y, w, h;

			x = boxStart.x;
			y = boxStart.y;

			w = Math.abs(boxEnd.x - boxStart.x);
			h = Math.abs(boxEnd.y - boxStart.y);

			// Fade out anything else:
			// top, bottom, left, right
			Color COL_DRAG_OUTSIDE = new Color(0x70000000, true);
			g2.setPaint(COL_DRAG_OUTSIDE);
			g2.fillRect(0, 0, getWidth(), y);
			g2.fillRect(0, y + h, getWidth(), getHeight());
			g2.fillRect(0, y, x, h);
			g2.fillRect(x + w, y, getWidth() - x - w, h);

			if (showCrosshairs)
			{
				// Crosshair
				g2.setPaint(Color.red);
				g2.setStroke(new BasicStroke(1.0f));
				g2.drawLine(x, y + h / 2, x + w - 1, y + h / 2);
				g2.drawLine(x + w / 2, y, x + w / 2, y + h - 1);
			}

			// Box
			g2.setPaint(Color.black);
			g2.setStroke(new BasicStroke(1.0f));
			g2.drawRect(x, y, w, h);
		}
		else if (showCrosshairs)
		{
			// Global Crosshairs -- only when the zoom-box is not active
			g2.setPaint(Color.red);
			g2.setStroke(new BasicStroke(1.0f));
			g2.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
			g2.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight());
		}

		// Status
		if (runningJobs > 0)
		{
			int wid = 10;
			g2.setPaint(Color.black);
			g.fillRect(getWidth() - wid - 2, getHeight() - wid - 2, wid + 2, wid + 2);
			g2.setPaint(Color.red);
			g.fillRect(getWidth() - wid, getHeight() - wid, wid, wid);
		}
	}
}
