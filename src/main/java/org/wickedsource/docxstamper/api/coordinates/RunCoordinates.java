package org.wickedsource.docxstamper.api.coordinates;

import org.docx4j.wml.R;

public class RunCoordinates extends AbstractCoordinates {

	private final R run;
	private final int index;

	public RunCoordinates(R run, int index) {
		this.run = run;
		this.index = index;
	}

	public R getRun() {
		return run;
	}

	public int getIndex() {
		return index;
	}

	@Override
	public String toString() {
		return String.format("run at index %d", index);
	}

}
