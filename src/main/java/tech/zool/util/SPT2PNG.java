package tech.zool.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

/**
 * <h2>DOS时代的金山WPS中SPT图形格式转换为PNG格式图片.</h2>
 * <h3>SPT数据格式：</h3>
 * <p>
 * 前64字节为文件头，后面是数据。 文件头前16个字节是"Super-Star File." 第34、35字节是图片宽度，第36、37字节是图片高度。 第39、40字节是是否压缩的标记。非压缩是01H,00H，压缩是05H,80H。
 * </p>
 * <p>
 * SPT图形是黑白图形。坐标是从左上角开始，从左到右，从上倒下，每个点1bit。
 * </p>
 * <p>
 * 非压缩数据：字节的每位对应图片左上角开始，从左到右从上倒下的点阵。
 * </p>
 * <p>
 * 压缩数据格式是：控制字节，数据，...，数据；控制字节,数据，...，数据；...；控制字节,数据，...，数据。 如果控制字节值≥128,则其后数据重复 (256-控制字节值+1) 次，否则，控制字节之后的 (控制字节值+1)
 * 个字节都为数据。
 * </p>
 *
 * @since 2013-8-19
 * @version 1.0
 * @author zooltech@qq.com
 */
public class SPT2PNG {

    /**
     * 字节蒙板.
     */
    private static final int MASK = 128;

    private static final String FILEHEAD_ERROR_MSG = "FileHeadError";
    private static final String IMAGE_INFO_MSG = "ImageInfo";
    private static final String FILE_NOT_EXIST = "FileNotExist";
    private static final String EXEC_FINISHED = "ExecFinished";
    private static final String HELP_MSG = "HelpMsg";
    private static final ResourceBundle msg = ResourceBundle.getBundle("message", Locale.getDefault());

    /**
     * 读取SPT文件.
     *
     * @param file SPT文件名
     * @return 图片数据
     * @throws IOException 文件操作发生问题时抛出
     */
    public BufferedImage readSPT(final String file) throws IOException {
        final File f = new File(file);
        return readSPT(f);
    }

    /**
     * 读取SPT文件.
     *
     * @param f SPT文件对象
     * @return 图片数据
     * @throws IOException 文件操作发生问题时抛出
     */
    public BufferedImage readSPT(final File f) throws IOException {
        BufferedImage img = null;
        InputStream is = null;
        try {
            is = new FileInputStream(f);
            final byte[] sptHead = new byte[64];
            final int res = is.read(sptHead);
            if (res != 64) {
                System.out.println(msg.getString(FILEHEAD_ERROR_MSG));
            }
            final int width = (sptHead[35] & 0xff) * 256 + (sptHead[34] & 0xff);
            final int height = (sptHead[37] & 0xff) * 256 + (sptHead[36] & 0xff);
            final boolean isCompact = (sptHead[39] & 0x80) == 128;
            System.out.println(MessageFormat.format(msg.getString(IMAGE_INFO_MSG), width, height, isCompact));
            if (isCompact) {
                img = compactSpt(is, width, height);
            } else {
                img = normalspt(is, width, height);
            }
            return img;
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return null;
    }

    /**
     * 非压缩SPT处理.
     *
     * @param is spt数据流
     * @param width 图片宽度
     * @param height 图片高度
     * @return 图片数据
     */
    private BufferedImage normalspt(final InputStream is, final int width, final int height) {
        final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        final int byteWidth = width >> 3;
        final byte[] dots = new byte[byteWidth];
        int color, y = 0;
        try {
            while (is.read(dots) != -1) {
                for (int i = 0; i < byteWidth; i++) {
                    final byte b = dots[i];
                    for (int mask = MASK, j = 0; mask > 0; mask >>>= 1, j++) {
                        if ((b & mask) != 0) {
                            color = 0xffffff;
                        } else {
                            color = 0;
                        }
                        img.setRGB(i * 8 + j, y, color);
                    }
                }
                y++;
            }
            return img;
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 压缩SPT处理.
     *
     * @param is spt数据流
     * @param width 图片宽度
     * @param height 图片高度
     * @return 图片数据
     */
    private BufferedImage compactSpt(final InputStream is, final int width, final int height) {
        final BufferedImage img = new BufferedImage(width, height + 1, BufferedImage.TYPE_INT_RGB);
        int data, d, color;
        // 像素指针
        long pos = 0;

        try {
            while ((data = is.read()) != -1) {
                if (data < 128) {
                    for (int i = 0; i <= data; i++) {
                        d = is.read();
                        for (int mask = MASK; mask > 0; mask >>>= 1) {
                            if ((d & mask) != 0) {
                                color = 0xffffff;
                            } else {
                                color = 0;
                            }
                            img.setRGB((int) (pos % width), (int) (pos / width), color);
                            pos++;
                        }
                    }
                } else {
                    d = is.read();
                    final int[] bs = new int[8];
                    for (int mask = MASK, j = 0; mask > 0; mask >>>= 1, j++) {
                        if ((d & mask) != 0) {
                            bs[j] = 0xffffff;
                        } else {
                            bs[j] = 0;
                        }
                    }
                    for (int i = 0, count = 256 - data; i <= count; i++) {
                        for (int j = 0; j < 8; j++) {
                            img.setRGB((int) (pos % width), (int) (pos / width), bs[j]);
                            pos++;
                        }
                    }
                }
                // 防止越界。SPT文件正常情况下不会发生。
                if (pos / width >= height) {
                    break;
                }
            }
            return img;
        } catch (final Exception e) {
            System.out.println((int) (pos % width) + "," + (int) (pos / width));
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param f 要进行转缓的目录或文件.
     * @throws IOException 文件操作发生问题时抛出
     */
    private void doTrans(final File f) throws IOException {
        if (!f.exists()) {
            System.out.println(msg.getString(FILE_NOT_EXIST));
            return;
        }
        if (f.isDirectory()) {
            final File[] sub = f.listFiles();
            if (sub == null || sub.length < 1) {
                return;
            }
            for (int i = 0; i < sub.length; i++) {
                doTrans(sub[i]);
            }
        } else if (f.isFile()) {
            String path = f.getAbsolutePath();
            if (path.toLowerCase(Locale.US).endsWith(".spt")) {
                System.out.print(path);
                path = path.substring(0, path.lastIndexOf('.')).concat(".png");
                System.out.println(" ==> " + path);
                final BufferedImage img = readSPT(f);
                try {
                    ImageIO.write(img, "PNG", new File(path));
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 将指定目录及其子目录中所有金山SPT图形文件转为PNG图形文件.
     *
     * @param args 命令行参数
     * @throws IOException 文件操作发生问题时抛出
     */
    public static void main(final String[] args) throws IOException {
        if (args == null || args.length < 1) {
            System.out.println(msg.getString(HELP_MSG));
            return;
        }
        new SPT2PNG().doTrans(new File(args[0]));
        System.out.println(msg.getString(EXEC_FINISHED));
    }
}
