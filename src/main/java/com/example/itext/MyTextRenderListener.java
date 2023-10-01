package com.example.itext;

import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.Matrix;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;

import java.util.ArrayList;
import java.util.List;

public class MyTextRenderListener implements RenderListener {
    private List<String> keywords = new ArrayList();
    private List<String> texts = new ArrayList();
    private List<List<Double>> positions = new ArrayList<>();

    /**
     * Constructor
     */
    public MyTextRenderListener() {
        this(new ArrayList<>());
    }

    /**
     * Constructor
     *
     * @param keywords Searching keyword list
     */
    public MyTextRenderListener(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getTexts() {
        return texts;
    }
    public List<List<Double>> getPositions() {
        return positions;
    }

    @Override
    public void beginTextBlock() {}

    @Override
    public void endTextBlock() {}

    @Override
    public void renderImage(ImageRenderInfo renderInfo) {}

    @Override
    public void renderText(TextRenderInfo renderInfo) {
        String text = renderInfo.getText();
        System.out.println("text => " + text);

        LineSegment segment = renderInfo.getBaseline();
        System.out.println("segment => " + segment.getBoundingRectange().getX() + " x " + segment.getBoundingRectange().getY());

        for (String keyword: this.keywords) {
            if (text != null && text.toLowerCase().contains(keyword.toLowerCase())) {
                // Found it
                LineSegment ls = renderInfo.getBaseline();
                double X = ls.getBoundingRectange().getX();
                double Y = ls.getBoundingRectange().getY();
                System.out.println("Found at X: " + X + ",  Y: " + Y);
                List<Double> position = new ArrayList<>();
                position.add(X);
                position.add(Y);
                this.positions.add(position);
            }
        }
    }
}
