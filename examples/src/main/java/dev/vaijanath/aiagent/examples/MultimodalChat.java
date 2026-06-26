package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.model.Media;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Multimodal input: attach an image to a user turn with {@link Message#user(String, List)} and send it to
 * a vision model. Multimodal is a model-level capability, so this calls the {@link ModelPort} directly;
 * text-only models ignore the media. The image is generated in-process, so the example is self-contained —
 * point {@code AGENT_MODEL} at a vision model (e.g. {@code llava}) to actually describe it.
 */
public final class MultimodalChat {

    private MultimodalChat() {}

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        System.out.println("== MultimodalChat ==  model: " + model.name());

        Message turn = Message.user(
                "Describe this chart in one sentence — which bar is tallest?",
                List.of(Media.image("image/png", barChartPng())));
        ModelResponse answer = model.chat(ModelRequest.of(List.of(turn)));
        System.out.println(answer.text());

        if (Examples.isStub(model)) {
            System.out.println("\n(stub model — set AGENT_MODEL to a vision model like 'llava' to read the image)");
        }
    }

    /** A small self-contained PNG: three bars of increasing height, so there's something to describe. */
    private static byte[] barChartPng() {
        BufferedImage img = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 240, 160);
        g.setColor(new Color(0x3B82F6));
        int[] heights = {40, 80, 130};
        for (int i = 0; i < heights.length; i++) {
            g.fillRect(30 + i * 70, 160 - heights[i] - 10, 50, heights[i]);
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to render the chart image", e);
        }
    }
}
