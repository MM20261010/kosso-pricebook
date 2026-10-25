#pragma once
#include <net.h>
#include <opencv2/core.hpp>
#include <string>
#include <vector>
namespace localocr {
struct Word { std::vector<cv::Point2f> box; std::string text; };
struct Document { int width=0,height=0; std::string text; std::vector<Word> regions; };
std::vector<unsigned char> encode(const Document& result);
class Pipeline {
public:
 ncnn::Net detector, recognizer;
 std::vector<std::string> keys;
 Pipeline();
 Document recognizeDocument(const cv::Mat& bgr);
 std::string recognize(const cv::Mat& bgr){return recognizeDocument(bgr).text;}
};
}
