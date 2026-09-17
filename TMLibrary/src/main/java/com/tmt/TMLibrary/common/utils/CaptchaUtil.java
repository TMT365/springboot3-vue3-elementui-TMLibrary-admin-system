package com.tmt.TMLibrary.common.utils;


import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.exception.BusinessException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * 这是一个用于生成图像验证码的工具类
 *
 * 字符集(2026-09 调整):大写字母 + 数字,排除 l/1/I/0/O 五个易混字符,
 * 剩 31 个字符。信息熵 log2(31^4) ≈ 19.7 bit,比纯数字高 ~50%。
 * 早版本用 62 个大小写+数字,因 l/1/I 字号宽度差异大,会裁字;
 * 排除歧义字符后,字符宽度均匀,4 个字符在 120px 容器内绰绰有余。
 */

public class CaptchaUtil {

    private static final int WIDTH = 120; // 宽度
    private static final int HEIGHT = 48; // 高度
    private static final int LENGTH = 4; // 验证码字符数量
    /**
     * 字符集 —— 大写 + 数字,排除 l/1/I/0/O(易混)
     *   - 排除: l(L 小写), 1(数字), I(大写 i), 0(零), O(大写 o)
     *   - 保留: A-Z 除 I/O = 24 字母 + 2-9 = 8 数字 = 32 字符
     *     (注意:数字段从 2-9,跳过 0 和 1)
     */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 字符水平间距 —— 留够每个字母的宽度 */
    private static final int CHAR_GAP = 25;
    /** 左右 padding,保证第一个和最后一个字符不被裁 */
    private static final int LEFT_PADDING = 20;
    /** baseline y 坐标 —— 字体 26px 放在 HEIGHT=48 容器里,baseline 34 让顶部留 8px、底部留 14px */
    private static final int BASELINE_Y = 34;
    /** y 方向随机偏移(±),让字符有错落感,但不能越界 */
    private static final int Y_JITTER = 4;
    private static final Random random = new Random();

    /**
     * 验证码图片生成结果 —— 文本(给后端存 Redis 用) + 图片字节(JPEG,给前端渲染用)。
     */
    public record CaptchaImage(String text, byte[] jpegBytes) {}

    /**
     * 生成验证码图片 —— 不再写 HttpServletResponse,改成返回 byte[],由 controller 决定
     * 怎么响应(2026-09 改为返回 JSON 对象,内含 base64 data URI + 过期时间戳)。
     *
     * @return 验证码文本 + JPEG 图片字节
     */
    public static CaptchaImage generateCaptchaImage() {
        // 创建图像, 这是一个包含图像数据的缓冲区
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        // 获取Graphics2D对象，可以在图像上进行绘制
        Graphics2D g2d = image.createGraphics();

        // 抗锯齿 —— 字符边缘更平滑,小尺寸下也能看清
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 设置图像的背景色
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // 设置字体 —— 26px 粗体,Times New Roman 字形清晰
        g2d.setFont(new Font("Times New Roman", Font.BOLD, 26));

        // 获取随机生成的验证码文本
        String captchaText = generateRandomCaptchaText();

        // 开始循环绘制 Graphics2D 上的字符
        for (int i = 0; i < LENGTH; i++) {
            char c = captchaText.charAt(i);
            int x = LEFT_PADDING + i * CHAR_GAP;
            // y 方向轻微抖动,字符错落不机械;±Y_JITTER 不会越界
            int y = BASELINE_Y + (random.nextInt(2 * Y_JITTER + 1) - Y_JITTER);
            // 为字符随机生成颜色,避开纯白(255,255,255)以保底可读
            g2d.setColor(new Color(
                20 + random.nextInt(200),   // 20-219 偏暗,不要纯白
                20 + random.nextInt(200),
                20 + random.nextInt(200)));
            g2d.drawString(String.valueOf(c), x, y);
        }

        // 添加噪点 —— 限制在图像范围内,不再画到画布外
        g2d.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < 30; i++) {
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int dx = random.nextInt(8) - 4; // -4 ~ +4
            int dy = random.nextInt(8) - 4;
            // 画到画布外没意义,clamp 到边界
            int x2 = Math.max(0, Math.min(WIDTH - 1, x1 + dx));
            int y2 = Math.max(0, Math.min(HEIGHT - 1, y1 + dy));
            g2d.drawLine(x1, y1, x2, y2);
        }

        // 完成图像的绘制，释放图形上下文使用的系统资源
        g2d.dispose();

        // 把 BufferedImage 编码成 JPEG 字节流(不再写 response)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "JPEG", baos);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "验证码生成失败", e);
        }

        return new CaptchaImage(captchaText, baos.toByteArray());
    }

    /**
     * 旧版 API:直接写 HttpServletResponse。
     * 2026-09 重构后保留,内部分发到 {@link #generateCaptchaImage()} 避免破坏外部调用方(若有)。
     */
    public static String CreateCaptchaImage(jakarta.servlet.http.HttpServletResponse response) {
        CaptchaImage ci = generateCaptchaImage();
        response.setContentType("image/jpeg");
        try {
            response.getOutputStream().write(ci.jpegBytes());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "验证码生成失败", e);
        }
        return ci.text();
    }

    /**
     * 生成随机文本，用于验证码
     *
     * @return 返回生成的随机文本
     */
    private static String generateRandomCaptchaText() {
        StringBuilder sb = new StringBuilder(CaptchaUtil.LENGTH);
        for (int i = 0; i < CaptchaUtil.LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

}
