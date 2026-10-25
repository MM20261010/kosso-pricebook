#pragma once
#include <net.h>
#include <opencv2/core.hpp>
#include <string>
#include <vector>
namespace localocr {
struct Word { std::vector<cv::Point2f> box; std::string text; };
class Pipeline {
public:
 ncnn::Net detector, recognizer;
 std::vector<std::string> keys;
 Pipeline();
 std::string recognize(const cv::Mat& bgr);
};
}
