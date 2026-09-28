# ImageLab — Image Processing Studio

A Java desktop application for loading, processing, analyzing, and managing digital images through a JavaFX graphical user interface. The project is developed using Java, JavaFX, Maven, and SQLite, with a focus on image processing, object-oriented programming, multithreading, database operations, JSON configuration, and REST API integration.

## Features

- Load images from the local system
- Display images through a JavaFX graphical interface
- Apply various image processing operations and filters
- Perform point processing such as Negative, Log, Power-Law, Brightness, and Contrast
- Perform color model conversions such as CMY, CMYK, HSI, YUV, and YCbCr
- Extract image channels such as RGB, Gray, Hue, Saturation, and Intensity
- Apply Mean, Gaussian, Median, Laplacian, and High-Boost filters
- Perform histogram visualization and histogram equalization
- Perform binary and grayscale morphological operations
- Perform image arithmetic and logical operations
- Apply geometric transformations such as rotation, mirroring, and cropping
- Support PNG, JPG, BMP, GIF, PPM, and YUV 4:4:4 formats
- Save and export processed images
- Perform image processing using multithreading
- Track image processing operations
- Store image metadata and processing history in a SQLite database
- Perform Create, Read, Update, and Delete (CRUD) operations on processing records
- Import and export processing history using JSON
- Load application configuration from a JSON file
- Fetch images using a public REST API
- Provide zoom controls from 10% to 800%
- Display image resolution, zoom level, and mouse coordinates
- Provide an in-app console for displaying processing logs

## Technologies Used

- **Java 21**
- **JavaFX 21.0.5**
- **Maven 3.13.0**
- **SQLite 3.47.0.0**
- **JDBC**
- **Gson 2.11.0**
- **Java HttpClient**
- **JavaFX CSS**
- **Git & GitHub**

## Java Concepts Demonstrated

This project demonstrates several core Java concepts:

- Object-Oriented Programming
- Classes and Objects
- Encapsulation
- Inheritance
- Polymorphism
- Abstraction
- Interfaces
- Exception Handling
- File Handling
- Collections
- Multithreading and Concurrency
- Background Tasks
- JDBC and Database Connectivity
- SQLite CRUD Operations
- JSON Parsing
- REST API Integration

## Image Processing Operations

### Point Operations

- Negative Transformation
- Log Transformation
- Power-Law (Gamma) Transformation
- Piecewise-Linear Transformation
- Brightness Adjustment
- Contrast Adjustment
- Bit-Plane Slicing

### Color Models

- CMY
- CMYK
- HSI
- YUV
- YCbCr

### Filters

- Mean Filter
- Gaussian Filter
- Median Filter
- Laplacian Filter
- High-Boost Filter

### Histogram

- Histogram Visualization
- Histogram Equalization

### Morphology

- Binary Erosion
- Binary Dilation
- Binary Opening
- Binary Closing
- Grayscale Erosion
- Grayscale Dilation
- Grayscale Opening
- Grayscale Closing

### Arithmetic and Logic Operations

- Image Addition
- Image Subtraction
- AND
- OR
- XOR

### Geometric Transformations

- Rotate 90° Clockwise
- Rotate 90° Counterclockwise
- Rotate 180°
- Horizontal Mirror
- Vertical Mirror
- Crop

## Supported File Formats

- PNG
- JPG / JPEG
- BMP
- GIF
- PPM (P3)
- PPM (P6)
- YUV 4:4:4

## Database

SQLite is used to store:

- Image metadata
- Processing history
- Applied operations
- Processing records

The application performs CRUD operations using JDBC.

## JSON and REST API

The application uses **Gson** for JSON processing.

JSON is used for:

- Application configuration
- Processing history export
- Processing history import
- REST API response parsing

Java's built-in `HttpClient` is used for REST API communication.

## Multithreading

Heavy image processing operations are performed using background threads so that the JavaFX user interface remains responsive during processing.

## Project Structure

```text
ImageLab/

│
├── data/
│   └── Database and application data
│
├── src/
│   └── main/
│       ├── java/
│       │   └── Application source code
│       │
│       └── resources/
│           ├── CSS/
│           ├── JSON configuration
│           └── Other JavaFX resources
│
├── pom.xml
├── .gitignore
└── README.md
