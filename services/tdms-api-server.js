/**
 * TDMS Signal Analysis API Server
 * 提供TDMS文件读取、分析和可视化的后端API
 */

const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const multer = require('multer');

const app = express();
const PORT = 3002;

// 文件上传配置
const UPLOAD_DIR = path.join(__dirname, '..', 'uploads');
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOAD_DIR),
  filename: (req, file, cb) => cb(null, `${Date.now()}-${file.originalname}`)
});
const upload = multer({ storage, limits: { fileSize: 500 * 1024 * 1024 } }); // 500MB limit

// 中间件
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// TDMS文件目录（使用项目根目录下的floatdata）
const PROJECT_ROOT = path.join(__dirname, '..');
const SIGNAL_1_DIR = path.join(PROJECT_ROOT, 'floatdata', 'signal-1');
const SIGNAL_2_DIR = path.join(PROJECT_ROOT, 'floatdata', 'signal-2');
const PYTHON_SCRIPTS_DIR = path.join(PROJECT_ROOT, 'floatdata', 'floatdata-streaming');

/**
 * 获取文件列表
 */
app.get('/api/tdms/files', (req, res) => {
  try {
    const signal1Files = [];
    const signal2Files = [];
    
    // 读取 Signal-1 文件
    if (fs.existsSync(SIGNAL_1_DIR)) {
      const files = fs.readdirSync(SIGNAL_1_DIR);
      files.forEach(file => {
        if (file.endsWith('.tdms')) {
          const filePath = path.join(SIGNAL_1_DIR, file);
          const stats = fs.statSync(filePath);
          signal1Files.push({
            name: file,
            path: `/floatdata/signal-1/${file}`,
            size: stats.size
          });
        }
      });
    }
    
    // 读取 Signal-2 文件
    if (fs.existsSync(SIGNAL_2_DIR)) {
      const files = fs.readdirSync(SIGNAL_2_DIR);
      files.forEach(file => {
        if (file.endsWith('.tdms')) {
          const filePath = path.join(SIGNAL_2_DIR, file);
          const stats = fs.statSync(filePath);
          signal2Files.push({
            name: file,
            path: `/floatdata/signal-2/${file}`,
            size: stats.size
          });
        }
      });
    }
    
    res.json({
      signal1: signal1Files,
      signal2: signal2Files
    });
  } catch (error) {
    console.error('获取文件列表失败:', error);
    res.status(500).json({ error: '获取文件列表失败' });
  }
});

/**
 * 获取文件信息
 */
app.get('/api/tdms/info', (req, res) => {
  try {
    const { path: filePath } = req.query;
    
    if (!filePath) {
      return res.status(400).json({ error: '缺少文件路径参数' });
    }
    
    const fullPath = path.join(__dirname, filePath);
    
    if (!fs.existsSync(fullPath)) {
      return res.status(404).json({ error: '文件不存在' });
    }
    
    const stats = fs.statSync(fullPath);
    
    // 调用Python脚本获取详细信息
    const python = spawn('python3', [
      path.join(PYTHON_SCRIPTS_DIR, 'tdms-info.py'),
      fullPath
    ]);
    
    let output = '';
    let errorOutput = '';
    
    python.stdout.on('data', (data) => {
      output += data.toString();
    });
    
    python.stderr.on('data', (data) => {
      errorOutput += data.toString();
    });
    
    python.on('close', (code) => {
      if (code === 0) {
        try {
          const info = JSON.parse(output);
          res.json(info);
        } catch (e) {
          // 返回基本信息
          res.json({
            name: path.basename(fullPath),
            size: stats.size,
            sampleRate: 100000,
            channels: 4,
            samples: 200000,
            duration: 2.0
          });
        }
      } else {
        console.error('Python错误:', errorOutput);
        // 返回基本信息
        res.json({
          name: path.basename(fullPath),
          size: stats.size,
          sampleRate: 100000,
          channels: 4,
          samples: 200000,
          duration: 2.0
        });
      }
    });
    
  } catch (error) {
    console.error('获取文件信息失败:', error);
    res.status(500).json({ error: '获取文件信息失败' });
  }
});

/**
 * 分析TDMS文件
 */
app.post('/api/tdms/analyze', (req, res) => {
  try {
    const { filePath, sampleRate = 100000, cutoffFreq = 10000, filterOrder = 6 } = req.body;
    
    if (!filePath) {
      return res.status(400).json({ error: '缺少文件路径参数' });
    }
    
    const fullPath = path.join(__dirname, filePath);
    
    if (!fs.existsSync(fullPath)) {
      return res.status(404).json({ error: '文件不存在' });
    }
    
    // 调用Python脚本进行信号分析
    const python = spawn('python3', [
      path.join(PYTHON_SCRIPTS_DIR, 'tdms-analyzer.py'),
      fullPath,
      sampleRate.toString(),
      cutoffFreq.toString(),
      filterOrder.toString()
    ]);
    
    let output = '';
    let errorOutput = '';
    
    python.stdout.on('data', (data) => {
      output += data.toString();
    });
    
    python.stderr.on('data', (data) => {
      errorOutput += data.toString();
    });
    
    python.on('close', (code) => {
      if (code === 0) {
        try {
          const result = JSON.parse(output);
          res.json(result);
        } catch (e) {
          console.error('解析Python输出失败:', e);
          res.status(500).json({ error: '解析分析结果失败' });
        }
      } else {
        console.error('Python分析错误:', errorOutput);
        res.status(500).json({ error: '信号分析失败', details: errorOutput });
      }
    });
    
  } catch (error) {
    console.error('分析失败:', error);
    res.status(500).json({ error: '分析失败' });
  }
});

/**
 * 分析TDMS文件夹（多文件组合分析）
 */
app.post('/api/tdms/analyze-folder', (req, res) => {
  try {
    const { folder, sampleRate = 100000, cutoffFreq = 10000, filterOrder = 6 } = req.body;
    
    if (!folder) {
      return res.status(400).json({ error: '缺少文件夹参数' });
    }
    
    let folderPath;
    if (folder === 'signal-1') {
      folderPath = path.join(PROJECT_ROOT, 'floatdata', 'signal-1', 'ae_sim_2s.tdms');
    } else if (folder === 'signal-2') {
      folderPath = path.join(PROJECT_ROOT, 'floatdata', 'signal-2');
    } else {
      return res.status(400).json({ error: '不支持的文件夹' });
    }
    
    console.log(`分析文件夹: ${folder}, 路径: ${folderPath}`);
    
    // 调用Python脚本进行文件夹分析
    const python = spawn('python3', [
      path.join(PYTHON_SCRIPTS_DIR, 'tdms-folder-analyzer.py'),
      folder,
      sampleRate.toString(),
      cutoffFreq.toString(),
      filterOrder.toString()
    ], {
      cwd: PROJECT_ROOT,  // 设置工作目录为项目根目录
      maxBuffer: 10 * 1024 * 1024 // 10MB buffer
    });
    
    let output = '';
    let errorOutput = '';
    
    python.stdout.on('data', (data) => {
      output += data.toString();
    });
    
    python.stderr.on('data', (data) => {
      errorOutput += data.toString();
      // 只记录关键错误信息
      if (data.toString().includes('ERROR') || data.toString().includes('Traceback')) {
        console.error('[Python Error]:', data.toString());
      }
    });
    
    python.on('close', (code) => {
      if (code === 0) {
        try {
          const result = JSON.parse(output);
          res.json(result);
        } catch (e) {
          console.error('解析Python输出失败:', e);
          console.error('输出长度:', output.length);
          console.error('输出前100字符:', output.substring(0, 100));
          res.status(500).json({ error: '解析分析结果失败', details: e.message });
        }
      } else {
        console.error('Python分析错误 (code:', code, ')');
        console.error('错误输出:', errorOutput);
        res.status(500).json({ error: '文件夹分析失败', details: errorOutput, code });
      }
    });
    
  } catch (error) {
    console.error('文件夹分析失败:', error);
    res.status(500).json({ error: '文件夹分析失败' });
  }
});

/**
 * 健康检查
 */
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'TDMS Analysis API',
    timestamp: new Date().toISOString()
  });
});

/**
 * 上传并分析TDMS文件
 */
app.post('/api/tdms/analyze-upload', upload.single('file'), (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '没有上传文件' });
    }
    
    const filePath = req.file.path;
    const sampleRate = req.body.sampleRate || 100000;
    const cutoffFreq = req.body.cutoffFreq || 10000;
    const filterOrder = req.body.filterOrder || 6;
    
    console.log(`分析上传文件: ${req.file.originalname}, 路径: ${filePath}`);
    
    // 调用Python脚本进行单文件分析
    const python = spawn('python3', [
      path.join(PYTHON_SCRIPTS_DIR, 'tdms-upload-analyzer.py'),
      filePath,
      sampleRate.toString(),
      cutoffFreq.toString(),
      filterOrder.toString()
    ], {
      cwd: PROJECT_ROOT,
      maxBuffer: 50 * 1024 * 1024 // 50MB buffer for large files
    });
    
    let output = '';
    let errorOutput = '';
    
    python.stdout.on('data', (data) => {
      output += data.toString();
    });
    
    python.stderr.on('data', (data) => {
      errorOutput += data.toString();
      if (data.toString().includes('ERROR') || data.toString().includes('Traceback')) {
        console.error('[Python Error]:', data.toString());
      }
    });
    
    python.on('close', (code) => {
      // 清理上传的临时文件
      fs.unlink(filePath, (err) => {
        if (err) console.error('清理临时文件失败:', err);
      });
      
      if (code === 0) {
        try {
          const result = JSON.parse(output);
          res.json(result);
        } catch (e) {
          console.error('解析Python输出失败:', e);
          res.status(500).json({ error: '解析分析结果失败', details: e.message });
        }
      } else {
        console.error('Python分析错误 (code:', code, ')');
        console.error('错误输出:', errorOutput);
        res.status(500).json({ error: '文件分析失败', details: errorOutput });
      }
    });
    
  } catch (error) {
    console.error('上传分析失败:', error);
    res.status(500).json({ error: '上传分析失败' });
  }
});

// 启动服务器
app.listen(PORT, () => {
  console.log('====================================');
  console.log('  TDMS Signal Analysis API Server');
  console.log('====================================');
  console.log(`✅ Server running on http://localhost:${PORT}`);
  console.log(`📁 Signal-1 Directory: ${SIGNAL_1_DIR}`);
  console.log(`📁 Signal-2 Directory: ${SIGNAL_2_DIR}`);
  console.log('====================================');
});

// 错误处理
app.use((err, req, res, next) => {
  console.error('服务器错误:', err);
  res.status(500).json({ error: '内部服务器错误' });
});
