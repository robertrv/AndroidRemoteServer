/*
 *  This file is part of Android Remote.
 *
 *  Android Remote is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Leeser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  Android Remote is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Leeser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.uoc.androidremote.operations;

import java.io.Serializable;

/**
 * Bean to serialize and deserialize applications running on android device.
 * 
 * @author angel
 */
public class AndroidRunningApplication implements Serializable, Comparable<AndroidRunningApplication> {

	private static final long serialVersionUID = 1L;

	private String name;

	private int importance;

	public final String getName() {
		return name;
	}

	public final void setName(String name) {
		this.name = name;
	}

	public final int getImportance() {
		return importance;
	}

	public final void setImportance(int importance) {
		this.importance = importance;
	}

	public int compareTo(AndroidRunningApplication o) {
		AndroidRunningApplication app = o;
		if (this.importance != app.importance) {
			return this.importance - app.importance;
		} else {
			return this.name.compareTo(app.getName());
		}

	}
}
