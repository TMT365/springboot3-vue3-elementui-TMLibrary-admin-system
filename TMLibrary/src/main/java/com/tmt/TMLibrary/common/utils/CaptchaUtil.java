package com.tmt.TMLibrary.common.utils;


import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 这是一个用于生成图像验证码的工具类
 */

public class CaptchaUtil {

    private static final int WIDTH = 80;//宽度
    private static final int HEIGHT = 40;//高度
    private static final int LENGTH = 4;//验证码字符数量
    private static final Random random = new Random();//随机产生字符颜色

    /**
     * {@link CreateCaptchaImage} 是一个产生验证码图片的静态函数，将响应内容放入响应体里面返回给前端
     *
     * @param response HttpServletResponse, 用于输出图像到客户端
     * @return 返回生成的验证码文本
     */
    public static String CreateCaptchaImage(HttpServletResponse response) {
        // 创建图像, 这是一个包含图像数据的缓冲区
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        // 获取Graphics2D对象，可以在图像上进行绘制
        Graphics2D g2d = image.createGraphics();

        // 设置图像的背景色
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // 设置字体
        g2d.setFont(new Font("Times New Roman", Font.BOLD, 18));

        // 获取随机生成的验证码文本
        String captchaText = generateRandomCaptchaText();

        // 定义横坐标开始位置
        int x = 10;
        int y = 20;

        // 开始循环绘制 Graphics2D 上的字符
        for (int i = 0; i < LENGTH; i++) {
            char c = captchaText.charAt(i);
            //为字符随机生成颜色, 不能到 （255，255，255）纯白
            g2d.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            // 绘制字符
            g2d.drawString(String.valueOf(c), x, y + random.nextInt(20));
            x += 20;
        }

        // 添加噪点，使图像中的验证码不易被自动识别
        g2d.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < 20; i++) {
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(12);
            int y2 = random.nextInt(12);
            g2d.drawLine(x1, y1, x1 + x2, y1 + y2);// 绘制一条小线段，代表噪点
        }

        // 完成图像的绘制，释放图形上下文使用的系统资源
        g2d.dispose();

        // 设置响应内容类型为JPEG图像
        response.setContentType("image/jpeg");
        // 使用 ImageIO 输出
        try {
            // 将创建的图像写入响应的输出流中
            ImageIO.write(image, "JPEG", response.getOutputStream());
        } catch (Exception e){
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "验证码生成失败", e);
        }

        return captchaText;
    }

    /**
     * 生成随机文本，用于验证码
     *
     * @return 返回生成的随机文本
     */
    private static String generateRandomCaptchaText() {
        // 定义可能出现在验证码中的字符
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        // 随机选择字符并添加到StringBuilder中
        StringBuilder sb = new StringBuilder(CaptchaUtil.LENGTH);
        for (int i = 0; i < CaptchaUtil.LENGTH; i++) {
            // 将StringBuilder转换为字符串并返回
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

}
