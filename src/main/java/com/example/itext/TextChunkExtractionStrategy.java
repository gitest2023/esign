package com.example.itext;

import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.Matrix;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextChunkExtractionStrategy implements RenderListener {
    private final List<TextChunk> textChunks = new ArrayList();

    @Override
    public void beginTextBlock() {}

    @Override
    public void endTextBlock() {
        Collections.sort(textChunks);
    }

    protected List<TextChunk> getTextChunks() {
        return textChunks;
    }

    @Override
    public void renderText(TextRenderInfo renderInfo) {
        LineSegment segment = renderInfo.getBaseline();

        if (renderInfo.getRise() != 0) {
            segment = segment.transformBy(new Matrix(0, -renderInfo.getRise()));
        }
        textChunks.add(new TextChunk(renderInfo.getText(), segment.getStartPoint(), segment.getEndPoint(), renderInfo.getSingleSpaceWidth()));
    }

    @Override
    public void renderImage(ImageRenderInfo renderInfo) { }

    protected boolean startsWithSpace(String str) {
        if (str.length() == 0) {
            return false;
        }
        return str.charAt(0) == ' ';
    }

    protected boolean endsWithSpace(String str) {
        if (str.length() == 0) {
            return false;
        }
        return str.charAt(str.length() - 1) == ' ';
    }

    protected boolean isChunkAtWordBoundary(TextChunk chunk, TextChunk previousChunk) {

        if (chunk.getCharSpaceWidth() < 0.1f) {
            return false;
        }

        float dist = chunk.distanceFromEndOf(previousChunk);

        if (dist < -chunk.getCharSpaceWidth() || dist > chunk.getCharSpaceWidth() / 2.0f) {
            return true;
        }

        return false;
    }

    public List<TextChunk> matchAllText(final String str) {
        if (str == null
                || str.isEmpty()) {
            return null;
        }

        List<TextChunk> textChunkList = new ArrayList();

        StringBuilder sb = new StringBuilder();

        Vector startLocation = null;

        TextChunk lastChunk = null;

        for (TextChunk chunk : getTextChunks()) {
            if (lastChunk == null) {
                if (eitherContainsText(chunk.getText(), str)) {
                    sb.append(chunk.getText());

                    startLocation = chunk.getStartLocation();

                    lastChunk = chunk;
                }
            } else {
                if (chunk.sameLine(lastChunk)) {
                    if (isChunkAtWordBoundary(chunk, lastChunk) && !startsWithSpace(chunk.getText()) && !endsWithSpace(lastChunk.getText())) {
                        sb.append(' ');
                    }

                    sb.append(chunk.getText());

                    if (eitherContainsText(sb.toString(), str)) {
                        lastChunk = chunk;
                    } else {
                        lastChunk = null;
                        sb.delete(0, sb.length());
                        startLocation = null;
                    }
                } else {
                    lastChunk = null;
                    sb.delete(0, sb.length());
                    startLocation = null;

                    if (eitherContainsText(chunk.getText(), str)) {
                        sb.append(chunk.getText());

                        startLocation = chunk.getStartLocation();
                        lastChunk = chunk;
                    }
                }
            }

            if (sb.toString().contains(str)) {
                textChunkList.add(new TextChunk(sb.toString(), startLocation, chunk.getEndLocation(), chunk.getCharSpaceWidth()));

                sb.delete(0, sb.length());
                startLocation = null;
            }
        }

        return textChunkList;
    }

    public TextChunk matchText(final String str) {
        if (str == null
                || str.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        Vector startLocation = null;

        TextChunk lastChunk = null;

        for (TextChunk chunk : getTextChunks()) {
            if (lastChunk == null) {
                if (eitherContainsText(chunk.getText(), str)) {
                    sb.append(chunk.getText());

                    startLocation = chunk.getStartLocation();

                    lastChunk = chunk;
                }
            } else {
                if (chunk.sameLine(lastChunk)) {
                    if (isChunkAtWordBoundary(chunk, lastChunk) && !startsWithSpace(chunk.getText()) && !endsWithSpace(lastChunk.getText())) {
                        sb.append(' ');
                    }

                    sb.append(chunk.getText());

                    if (eitherContainsText(sb.toString(), str)) {
                        lastChunk = chunk;
                    } else {
                        lastChunk = null;
                        sb.delete(0, sb.length());
                        startLocation = null;
                    }
                } else {
                    lastChunk = null;
                    sb.delete(0, sb.length());
                    startLocation = null;

                    if (eitherContainsText(chunk.getText(), str)) {
                        sb.append(chunk.getText());

                        startLocation = chunk.getStartLocation();
                        lastChunk = chunk;
                    }
                }
            }

            if (sb.toString().length() > 4) {
                System.out.println();
            }
            if (sb.toString().contains(str)) {
                return new TextChunk(sb.toString(), startLocation, chunk.getEndLocation(), chunk.getCharSpaceWidth());
            }
        }

        return null;
    }

    private boolean eitherContainsText(String s1, String s2) {
        return s1.contains(s2) || s2.contains(s1);
    }

    public static class TextChunk implements Comparable<TextChunk> {

        private final String text;
        private final Vector startLocation;
        private final Vector endLocation;
        private final Vector orientationVector;
        private final int orientationMagnitude;
        private final int distPerpendicular;
        private final float distParallelStart;
        private final float distParallelEnd;
        private final float charSpaceWidth;

        public TextChunk(String string, Vector startLocation, Vector endLocation, float charSpaceWidth) {
            this.text = string;
            this.startLocation = startLocation;
            this.endLocation = endLocation;
            this.charSpaceWidth = charSpaceWidth;

            Vector oVector = endLocation.subtract(startLocation);
            if (oVector.length() == 0) {
                oVector = new Vector(1, 0, 0);
            }
            orientationVector = oVector.normalize();
            orientationMagnitude = (int) (Math.atan2(orientationVector.get(Vector.I2), orientationVector.get(Vector.I1)) * 1000);
            Vector origin = new Vector(0, 0, 1);
            distPerpendicular = (int) (startLocation.subtract(origin)).cross(orientationVector).get(Vector.I3);
            distParallelStart = orientationVector.dot(startLocation);
            distParallelEnd = orientationVector.dot(endLocation);
        }

        public Vector getStartLocation() {
            return startLocation;
        }

        public Vector getEndLocation() {
            return endLocation;
        }

        public String getText() {
            return text;
        }

        public float getCharSpaceWidth() {
            return charSpaceWidth;
        }

        public boolean sameLine(TextChunk as) {
            if (orientationMagnitude != as.orientationMagnitude) {
                return false;
            }

            if (distPerpendicular != as.distPerpendicular) {
                return false;
            }

            return true;
        }

        public float distanceFromEndOf(TextChunk other) {
            return distParallelStart - other.distParallelEnd;
        }

        @Override
        public int compareTo(TextChunk rhs) {
            if (this == rhs) {
                return 0;
            }
            int rslt;
            rslt = compareInts(orientationMagnitude, rhs.orientationMagnitude);
            if (rslt != 0) {
                return rslt;
            }

            rslt = compareInts(distPerpendicular, rhs.distPerpendicular);
            if (rslt != 0) {
                return rslt;
            }

            return Float.compare(distParallelStart, rhs.distParallelStart);
        }

        private static int compareInts(int int1, int int2) {
            return int1 == int2 ? 0 : int1 < int2 ? -1 : 1;
        }
    }
}
