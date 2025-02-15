/*
	Copyright 2009 Peter Hofmann

	This file is part of Multifrac.

	Multifrac is free software: you can redistribute it and/or modify it
	under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	Multifrac is distributed in the hope that it will be useful, but
	WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
	General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with Multifrac. If not, see <http://www.gnu.org/licenses/>.
*/

package multifrac;

import java.util.Deque;
import java.util.ArrayDeque;

public class ParameterStack
{
	private FractalParameters current = new FractalParameters();
	private Deque<FractalParameters> undo = new ArrayDeque<FractalParameters>();
	private Deque<FractalParameters> redo = new ArrayDeque<FractalParameters>();

	public FractalParameters get()
	{
		return current;
	}

	public void push()
	{
		// Save current element
		undo.offerFirst(current);

		// Clear redo stack, when the user makes a change
		redo.clear();

		// Create a copy of it and set this copy as the current element
		current = new FractalParameters(current);
	}

	public void pop()
	{
		if (undo.isEmpty())
			return;

		// Save the current element onto the redo stack
		redo.offerFirst(current);

		// Restore the last saved element
		current = undo.pollFirst();
	}

	public void unpop()
	{
		if (redo.isEmpty())
			return;

		// Re-place the current item on the undo-stack
		undo.offerFirst(current);
		
		// Re-activate first redo-item
		current = redo.pollFirst();
	}

	public void clear(FractalParameters top)
	{
		undo.clear();
		redo.clear();
		current = top;
	}
}
