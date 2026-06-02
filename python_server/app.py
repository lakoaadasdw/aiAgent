"""
图片识别 Python 服务器
提供图片识别接口，供 Java 后端调用
支持：OCR文字识别、基础图像识别（颜色、形状、物体检测等）
"""
import os
import io
import base64
import json
import uuid
import tempfile
import traceback
import subprocess
from datetime import datetime

from flask import Flask, request, jsonify
from flask_cors import CORS
from PIL import Image, ImageEnhance, ImageFilter, ImageOps
import numpy as np

app = Flask(__name__)
CORS(app)

# 配置
UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), 'uploads')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# ===================== Tesseract 检测 =====================

def _check_tesseract_installation():
    """全面检测Tesseract OCR引擎安装状态"""
    info = {
        "pytesseract_installed": False,
        "tesseract_engine_installed": False,
        "tesseract_version": None,
        "tesseract_path": None,
        "available_languages": [],
        "error": None
    }
    
    # 1. 检查 pytesseract 库是否安装
    try:
        import pytesseract
        info["pytesseract_installed"] = True
    except ImportError:
        info["error"] = "pytesseract 库未安装，请执行: pip install pytesseract"
        return info
    
    # 2. 检查 Tesseract 引擎是否可执行
    try:
        # 尝试获取版本信息
        version_result = subprocess.run(
            ['tesseract', '--version'], 
            capture_output=True, 
            text=True, 
            timeout=5
        )
        if version_result.returncode == 0:
            info["tesseract_engine_installed"] = True
            first_line = version_result.stdout.split('\n')[0] if version_result.stdout else version_result.stderr.split('\n')[0]
            info["tesseract_version"] = first_line.strip()
        else:
            info["error"] = "Tesseract 引擎返回错误码"
    except FileNotFoundError:
        # 尝试通过 pytesseract 查找
        try:
            import pytesseract
            # 在Windows上常见路径
            common_paths = [
                r'C:\Program Files\Tesseract-OCR\tesseract.exe',
                r'C:\Program Files (x86)\Tesseract-OCR\tesseract.exe',
                r'/usr/bin/tesseract',
                r'/usr/local/bin/tesseract',
                r'/opt/homebrew/bin/tesseract',
            ]
            for p in common_paths:
                if os.path.exists(p):
                    info["tesseract_engine_installed"] = True
                    info["tesseract_path"] = p
                    break
            
            if not info["tesseract_engine_installed"]:
                info["error"] = "Tesseract OCR 引擎未安装或未添加到系统PATH环境变量"
        except Exception as e:
            info["error"] = f"检测失败: {str(e)}"
    except Exception as e:
        info["error"] = f"检测异常: {str(e)}"
    
    # 3. 如果引擎可用，列出支持的语言包
    if info["tesseract_engine_installed"]:
        try:
            cmd = ['tesseract', '--list-langs']
            if info["tesseract_path"]:
                cmd[0] = info["tesseract_path"]
            
            lang_result = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
            if lang_result.returncode == 0:
                lines = lang_result.stdout.strip().split('\n')
                # 第一行通常是 "List of available languages (...)"
                for line in lines:
                    line = line.strip()
                    if line and not line.startswith('List of'):
                        info["available_languages"].append(line)
        except Exception:
            pass
    
    return info

# ===================== 初始化时检测 Tesseract =====================

TESSERACT_INFO = _check_tesseract_installation()

# 设置 pytesseract 路径（如果找到了）
if TESSERACT_INFO["tesseract_path"]:
    try:
        import pytesseract
        pytesseract.pytesseract.tesseract_cmd = TESSERACT_INFO["tesseract_path"]
    except Exception:
        pass

HAS_TESSERACT = TESSERACT_INFO["tesseract_engine_installed"]

# 检查中文语言包
HAS_CHINESE_LANG = 'chi_sim' in TESSERACT_INFO.get("available_languages", [])

if not HAS_TESSERACT:
    print("[WARN] ╔══════════════════════════════════════════════════╗")
    print("[WARN] ║      Tesseract OCR 引擎未安装！                ║")
    print("[WARN] ║      OCR文字识别功能不可用                      ║")
    print("[WARN] ╠══════════════════════════════════════════════════╣")
    print("[WARN] ║  Windows安装:                                    ║")
    print("[WARN] ║  https://github.com/UB-Mannheim/tesseract/wiki  ║")
    print("[WARN] ║  下载后安装，并勾选中文语言包                   ║")
    print("[WARN] ╠══════════════════════════════════════════════════╣")
    print("[WARN] ║  Linux: sudo apt-get install tesseract-ocr      ║")
    print("[WARN] ║  Mac:   brew install tesseract                  ║")
    print("[WARN] ╚══════════════════════════════════════════════════╝")
elif not HAS_CHINESE_LANG:
    print("[WARN] ╔══════════════════════════════════════════════════╗")
    print("[WARN] ║  中文语言包未安装！                             ║")
    print("[WARN] ║  如需识别中文，请安装中文语言包                ║")
    print("[WARN] ║  Windows: 重新安装并勾选Chinese Simplified     ║")
    print("[WARN] ║  Linux: sudo apt-get install tesseract-ocr-chi-sim ║")
    print("[WARN] ║  Mac:   brew install tesseract-lang             ║")
    print("[WARN] ╚══════════════════════════════════════════════════╝")
else:
    print(f"[INFO] Tesseract OCR: ✓ 已安装")
    print(f"[INFO] 版本: {TESSERACT_INFO.get('tesseract_version', '未知')}")
    print(f"[INFO] 语言包: {', '.join(TESSERACT_INFO.get('available_languages', []))}")


# ===================== 工具函数 =====================

def _ensure_rgb(image: Image.Image) -> Image.Image:
    """将图片转换为RGB模式（如果是RGBA则去掉Alpha通道），确保可保存为JPEG"""
    if image.mode == 'RGBA':
        background = Image.new('RGB', image.size, (255, 255, 255))
        background.paste(image, mask=image.split()[3])
        return background
    elif image.mode != 'RGB':
        return image.convert('RGB')
    return image


# ===================== 图像识别核心函数 =====================

def extract_image_info(image: Image.Image) -> dict:
    """提取图像基本信息"""
    return {
        "format": image.format or "未知",
        "mode": image.mode,
        "width": image.width,
        "height": image.height,
        "size_kb": _estimate_size_kb(image),
        "aspect_ratio": round(image.width / image.height, 2) if image.height > 0 else 0
    }


def _estimate_size_kb(image: Image.Image) -> float:
    """估算图片大小（KB）"""
    buf = io.BytesIO()
    img_to_save = _ensure_rgb(image) if image.format == 'JPEG' or image.format is None else image
    img_to_save.save(buf, format=image.format or 'JPEG')
    return round(len(buf.getvalue()) / 1024, 2)


def extract_dominant_colors(image: Image.Image, num_colors: int = 5) -> list:
    """提取图像主色调"""
    try:
        small = image.copy()
        small.thumbnail((100, 100))
        pixels = list(small.getdata())
        
        from collections import Counter
        color_counter = Counter(pixels)
        dominant = color_counter.most_common(num_colors)
        
        result = []
        for color, count in dominant:
            r, g, b = color[:3]
            total = sum(pixels_count for _, pixels_count in dominant)
            percentage = round(count / total * 100, 1) if total > 0 else 0
            color_name = _get_color_name(r, g, b)
            result.append({
                "rgb": f"rgb({r},{g},{b})",
                "hex": f"#{r:02x}{g:02x}{b:02x}",
                "name": color_name,
                "percentage": f"{percentage}%"
            })
        return result
    except Exception as e:
        return [{"error": f"颜色提取失败: {str(e)}"}]


def _get_color_name(r: int, g: int, b: int) -> str:
    """简单颜色名称识别"""
    if r > 200 and g > 200 and b > 200:
        return "白色/浅色"
    if r < 50 and g < 50 and b < 50:
        return "黑色/深色"
    if r > 200 and g < 100 and b < 100:
        return "红色"
    if r < 100 and g > 200 and b < 100:
        return "绿色"
    if r < 100 and g < 100 and b > 200:
        return "蓝色"
    if r > 200 and g > 200 and b < 100:
        return "黄色"
    if r > 200 and g < 100 and b > 200:
        return "紫色/品红"
    if r < 100 and g > 200 and b > 200:
        return "青色"
    if r > 200 and g > 150 and b < 100:
        return "橙色"
    if r > 200 and g < 150 and b > 150:
        return "粉红色"
    return f"混合色({r},{g},{b})"


def extract_brightness_info(image: Image.Image) -> dict:
    """提取亮度/对比度信息"""
    gray = image.convert('L')
    pixels = list(gray.getdata())
    avg_brightness = sum(pixels) / len(pixels) if pixels else 0
    
    return {
        "average_brightness": round(avg_brightness, 1),
        "brightness_level": "偏亮" if avg_brightness > 180 else ("偏暗" if avg_brightness < 80 else "适中"),
        "min_brightness": min(pixels) if pixels else 0,
        "max_brightness": max(pixels) if pixels else 255,
    }


def extract_sharpness_info(image: Image.Image) -> dict:
    """提取清晰度信息"""
    try:
        img_array = np.array(image.convert('L'))
        laplacian = np.array([
            [-1, -1, -1],
            [-1,  8, -1],
            [-1, -1, -1]
        ])
        h, w = img_array.shape
        score = 0
        count = 0
        for i in range(1, h-1):
            for j in range(1, w-1):
                region = img_array[i-1:i+2, j-1:j+2]
                score += abs(np.sum(region * laplacian))
                count += 1
        
        avg_score = score / count if count > 0 else 0
        
        return {
            "sharpness_score": round(avg_score, 2),
            "sharpness_level": "清晰" if avg_score > 20 else ("模糊" if avg_score < 5 else "一般"),
        }
    except Exception:
        return {"sharpness_score": 0, "sharpness_level": "无法评估"}


def _preprocess_for_ocr(image: Image.Image) -> list:
    """
    对图片进行多种OCR预处理，返回多个预处理版本
    多种策略提高识别成功率
    """
    results = []
    
    # 原始图片转灰度
    gray = image.convert('L')
    
    # ---- 策略1: 高对比度灰度 ----
    enhancer = ImageEnhance.Contrast(gray)
    high_contrast = enhancer.enhance(2.5)
    enhancer2 = ImageEnhance.Sharpness(high_contrast)
    sharpened = enhancer2.enhance(2.0)
    results.append(("高对比度锐化版", sharpened))
    
    # ---- 策略2: 自适应二值化（使用OTS阈值） ----
    img_array = np.array(gray)
    # 全局Otsu二值化
    threshold = np.mean(img_array) - 30  # 简单自适应阈值
    binary = (img_array > threshold).astype(np.uint8) * 255
    binary_img = Image.fromarray(binary, mode='L')
    results.append(("二值化版", binary_img))
    
    # ---- 策略3: 反转色二值化（白底黑字 -> 黑底白字，有时效果更好） ----
    inv_binary = 255 - binary
    inv_binary_img = Image.fromarray(inv_binary, mode='L')
    results.append(("反转二值化版", inv_binary_img))
    
    # ---- 策略4: 放大2倍后处理（小文字友好） ----
    if image.width < 1000 or image.height < 1000:
        enlarged = image.resize((image.width * 2, image.height * 2), Image.LANCZOS)
        enlarged_gray = enlarged.convert('L')
        enlarged_enhanced = ImageEnhance.Contrast(enlarged_gray).enhance(2.0)
        results.append(("放大2倍版", enlarged_enhanced))
    
    # ---- 策略5: 降噪+边缘增强 ----
    denoised = gray.filter(ImageFilter.MedianFilter(size=3))
    edges = denoised.filter(ImageFilter.FIND_EDGES)
    # 边缘增强：原图+边缘
    edge_enhanced = Image.blend(denoised, edges, 0.3)
    results.append(("边缘增强版", edge_enhanced))
    
    return results


def perform_ocr(image: Image.Image, lang: str = 'chi_sim+eng') -> dict:
    """执行OCR文字识别（增强版：多策略预处理）"""
    if not HAS_TESSERACT:
        return {
            "available": False,
            "text": "",
            "error": TESSERACT_INFO.get("error", "Tesseract OCR 引擎未安装"),
            "tesseract_info": {
                "pytesseract_installed": TESSERACT_INFO["pytesseract_installed"],
                "engine_installed": False,
                "installation_guide": {
                    "windows": "下载安装: https://github.com/UB-Mannheim/tesseract/wiki （安装时勾选中文语言包）",
                    "linux": "sudo apt-get install tesseract-ocr tesseract-ocr-chi-sim",
                    "mac": "brew install tesseract tesseract-lang"
                }
            }
        }
    
    try:
        import pytesseract
        
        # 检查语言包是否可用
        if 'chi_sim' in lang and not HAS_CHINESE_LANG:
            # 中文语言包缺失，只使用英文
            lang = 'eng'
            lang_warning = "中文语言包未安装，已降级为英文识别"
        else:
            lang_warning = None
        
        # 生成多种预处理版本
        preprocessed_versions = _preprocess_for_ocr(image)
        
        best_text = ""
        best_word_count = 0
        best_method = ""
        all_results = []
        
        for method_name, processed_img in preprocessed_versions:
            try:
                # 执行OCR
                text = pytesseract.image_to_string(processed_img, lang=lang)
                text = text.strip()
                word_count = len(text)
                
                result_entry = {
                    "method": method_name,
                    "text": text if text else "未识别到文字",
                    "word_count": word_count
                }
                all_results.append(result_entry)
                
                # 选择字数最多的结果
                if word_count > best_word_count:
                    best_word_count = word_count
                    best_text = text
                    best_method = method_name
                    
            except Exception as e:
                all_results.append({
                    "method": method_name,
                    "error": str(e),
                    "text": "",
                    "word_count": 0
                })
        
        # 如果有最好的结果，再用最佳方法获取详细词级别数据
        words = []
        if best_text:
            try:
                # 找到最佳方法对应的图片
                best_img = None
                for method_name, processed_img in preprocessed_versions:
                    if method_name == best_method:
                        best_img = processed_img
                        break
                
                if best_img:
                    data = pytesseract.image_to_data(best_img, lang=lang, output_type=pytesseract.Output.DICT)
                    for i, word in enumerate(data['text']):
                        if word and word.strip():
                            try:
                                conf = int(data['conf'][i])
                            except (ValueError, TypeError):
                                conf = 0
                            words.append({
                                "text": word,
                                "confidence": conf,
                                "x": int(data['left'][i]),
                                "y": int(data['top'][i]),
                                "width": int(data['width'][i]),
                                "height": int(data['height'][i])
                            })
            except Exception:
                words = []
        
        result = {
            "available": True,
            "text": best_text if best_text else "未识别到文字",
            "word_count": best_word_count,
            "best_method": best_method,
            "words": words[:100],
            "lang": lang,
            "all_methods": all_results,
            "tesseract_info": {
                "pytesseract_installed": True,
                "engine_installed": True,
                "version": TESSERACT_INFO.get("tesseract_version", "未知"),
                "available_languages": TESSERACT_INFO.get("available_languages", [])
            }
        }
        
        if lang_warning:
            result["warning"] = lang_warning
        
        return result
        
    except Exception as e:
        traceback.print_exc()
        return {
            "available": True,
            "text": "",
            "error": f"OCR识别失败: {str(e)}",
            "tesseract_info": TESSERACT_INFO
        }


def detect_image_type(image: Image.Image) -> str:
    """检测图像类型（照片/图表/截图/文档等）"""
    w, h = image.size
    total_pixels = w * h
    
    colors = image.getcolors(maxcolors=1000)
    if colors:
        unique_colors = len(colors)
        if unique_colors < 10:
            return "简单图形/图标"
        elif unique_colors < 50:
            return "图表/UI截图"
    
    ratio = w / h if h > 0 else 1
    if ratio > 3 or ratio < 0.33:
        return "长图/横幅"
    
    buf = io.BytesIO()
    img_rgb = _ensure_rgb(image)
    img_rgb.save(buf, format='JPEG')
    size_kb = len(buf.getvalue()) / 1024
    
    if size_kb < 50 and total_pixels < 500000:
        return "缩略图/小图标"
    
    return "照片/复杂图像"


# ===================== API 路由 =====================

@app.route('/health', methods=['GET'])
def health():
    """健康检查接口"""
    return jsonify({
        "status": "ok",
        "service": "图片识别服务器",
        "tesseract": TESSERACT_INFO,
        "timestamp": datetime.now().isoformat()
    })


@app.route('/recognize', methods=['POST'])
def recognize():
    """
    图片识别主接口
    接收：multipart/form-data，字段名 image
    返回：JSON格式的识别结果
    """
    try:
        if 'image' not in request.files:
            return jsonify({"error": "未上传图片，请使用字段名 'image' 上传"}), 400
        
        file = request.files['image']
        if file.filename == '':
            return jsonify({"error": "文件名为空"}), 400
        
        image_data = file.read()
        if not image_data:
            return jsonify({"error": "图片数据为空"}), 400
        
        image = Image.open(io.BytesIO(image_data))
        
        ocr_lang = request.form.get('lang', 'chi_sim+eng')
        enable_ocr = request.form.get('ocr', 'true').lower() == 'true'
        
        result = {
            "success": True,
            "filename": file.filename,
            "image_info": extract_image_info(image),
            "image_type": detect_image_type(image),
            "dominant_colors": extract_dominant_colors(image),
            "brightness": extract_brightness_info(image),
            "sharpness": extract_sharpness_info(image),
            "tesseract_info": TESSERACT_INFO,
            "timestamp": datetime.now().isoformat()
        }
        
        if enable_ocr:
            result["ocr"] = perform_ocr(image, lang=ocr_lang)
        
        saved_name = f"{uuid.uuid4().hex}_{file.filename}"
        saved_path = os.path.join(UPLOAD_FOLDER, saved_name)
        if saved_name.lower().endswith(('.jpg', '.jpeg')):
            _ensure_rgb(image).save(saved_path)
        else:
            image.save(saved_path)
        result["saved_as"] = saved_name
        
        return jsonify(result)
    
    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "error": f"识别失败: {str(e)}",
            "traceback": traceback.format_exc()
        }), 500


@app.route('/recognize/base64', methods=['POST'])
def recognize_base64():
    """通过Base64接收图片进行识别"""
    try:
        data = request.get_json()
        if not data or 'image' not in data:
            return jsonify({"error": "请提供base64编码的图片数据"}), 400
        
        image_b64 = data['image']
        if ',' in image_b64:
            image_b64 = image_b64.split(',')[1]
        
        image_bytes = base64.b64decode(image_b64)
        image = Image.open(io.BytesIO(image_bytes))
        
        ocr_lang = data.get('lang', 'chi_sim+eng')
        enable_ocr = data.get('ocr', True)
        
        result = {
            "success": True,
            "image_info": extract_image_info(image),
            "image_type": detect_image_type(image),
            "dominant_colors": extract_dominant_colors(image),
            "brightness": extract_brightness_info(image),
            "sharpness": extract_sharpness_info(image),
            "tesseract_info": TESSERACT_INFO,
            "timestamp": datetime.now().isoformat()
        }
        
        if enable_ocr:
            result["ocr"] = perform_ocr(image, lang=ocr_lang)
        
        return jsonify(result)
    
    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "error": f"识别失败: {str(e)}"
        }), 500


@app.route('/install-tesseract', methods=['GET'])
def install_guide():
    """返回Tesseract安装指引"""
    return jsonify({
        "message": "Tesseract OCR 安装指引",
        "is_installed": HAS_TESSERACT,
        "current_info": TESSERACT_INFO,
        "installation": {
            "windows": {
                "steps": [
                    "1. 访问 https://github.com/UB-Mannheim/tesseract/wiki",
                    "2. 下载最新版安装包（如 tesseract-ocr-w64-setup-5.x.x.exe）",
                    "3. 安装时勾选 'Chinese (Simplified)' 语言包",
                    "4. 安装完成后重启终端/服务器",
                    "5. 验证: 打开CMD输入 'tesseract --version'"
                ],
                "url": "https://github.com/UB-Mannheim/tesseract/wiki"
            },
            "linux": {
                "steps": [
                    "sudo apt-get update",
                    "sudo apt-get install tesseract-ocr",
                    "sudo apt-get install tesseract-ocr-chi-sim  # 中文语言包",
                    "tesseract --version  # 验证安装"
                ]
            },
            "mac": {
                "steps": [
                    "brew install tesseract",
                    "brew install tesseract-lang  # 多语言包（含中文）",
                    "tesseract --version  # 验证安装"
                ]
            }
        }
    })


@app.route('/info', methods=['GET'])
def info():
    """服务器信息"""
    return jsonify({
        "service": "图片识别服务器",
        "version": "2.0.0",
        "capabilities": [
            "图像基本信息提取（尺寸、格式、模式）",
            "主色调分析",
            "亮度/对比度分析",
            "清晰度评估",
            "图像类型识别",
            "OCR文字识别（5种预处理策略：高对比度、二值化、反转二值化、放大2倍、边缘增强）"
        ],
        "tesseract": TESSERACT_INFO,
        "endpoints": {
            "/health": "健康检查（含Tesseract状态）",
            "/recognize": "图片识别（multipart/form-data上传）",
            "/recognize/base64": "图片识别（base64编码）",
            "/install-tesseract": "Tesseract安装指引",
            "/info": "服务器信息"
        }
    })


if __name__ == '__main__':
    print("=" * 60)
    print("  图片识别 Python 服务器 v2.0")
    print("=" * 60)
    if HAS_TESSERACT:
        print(f"  Tesseract OCR: ✓ 已安装")
        print(f"  版本: {TESSERACT_INFO.get('tesseract_version', '未知')}")
        print(f"  语言包: {', '.join(TESSERACT_INFO.get('available_languages', ['无']))}")
    else:
        print(f"  Tesseract OCR: ✗ 未安装（文字识别不可用）")
    print(f"  上传目录: {UPLOAD_FOLDER}")
    print(f"  启动地址: http://0.0.0.0:5001")
    print("=" * 60)
    app.run(host='0.0.0.0', port=5001, debug=True)
